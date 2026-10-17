package me.weishu.kernelsu.ui.viewmodel

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.system.Os
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.data.model.HuskyRelease
import me.weishu.kernelsu.data.model.LkmAsset
import me.weishu.kernelsu.data.model.assessLkmUpdate
import me.weishu.kernelsu.data.model.LkmUpdateAvailability
import me.weishu.kernelsu.data.model.LkmUpdateAssessment
import me.weishu.kernelsu.data.repository.ReleaseHttpException
import me.weishu.kernelsu.data.repository.EnvCheckRepository
import me.weishu.kernelsu.data.repository.EnvCheckRepositoryImpl
import me.weishu.kernelsu.data.repository.HuskyReleaseRepository
import me.weishu.kernelsu.data.repository.HuskyReleaseRepositoryImpl
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.home.HomeUiState
import me.weishu.kernelsu.ui.screen.home.HuskyUpdateStatus
import me.weishu.kernelsu.ui.screen.home.SystemInfo
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import me.weishu.kernelsu.ui.util.getSELinuxStatusRaw
import me.weishu.kernelsu.ui.util.LoaderClient
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.resolveDeviceName
import me.weishu.kernelsu.ui.util.rootAvailable
import java.io.File

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl(),
    private val huskyRepo: HuskyReleaseRepository = HuskyReleaseRepositoryImpl(),
    private val envCheckRepo: EnvCheckRepository = EnvCheckRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState(checkRoot = false))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _flashLkm = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val flashLkm: SharedFlow<Uri> = _flashLkm.asSharedFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val runtimeRepo = me.weishu.kernelsu.data.repository.HomeRuntimeRepository()
    private var refreshJob: kotlinx.coroutines.Job? = null

    fun resumeObservation() { runtimeRepo.start() }
    fun pauseObservation() {
        runtimeRepo.pause()
        _uiState.update { it.copy(runtime = it.runtime.pause()) }
    }

    fun refresh(includeEnvironment: Boolean = true) {
        if (includeEnvironment) refreshEnvSummary()
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val (baseState, abDevice) = withContext(Dispatchers.IO) {
                me.weishu.kernelsu.data.repository.ModuleRebootStore.refreshBoot()
                val state = buildState()
                val faults = buildSet {
                    if (state.isSafeMode) add("safe_mode")
                    if (state.ksuVersion != null && !state.isRootAvailable) add("root_unavailable")
                    if (state.requiresNewKernel || state.showVersionMismatchWarning) add("interface_mismatch")
                }
                state.copy(loaded = true, observedAt = android.os.SystemClock.elapsedRealtime(), observedWallTime = System.currentTimeMillis(),
                    runtime = runtimeRepo.observe(state.ksuVersion != null, faults), runtimeError = runtimeRepo.error,
                    pageSize = runCatching { me.weishu.kernelsu.ui.util.getKernelPageSize() }.getOrNull(),
                    slot = runCatching { me.weishu.kernelsu.ui.util.getSlotSuffix(false) }.getOrNull()) to isAbDevice()
            }
            _uiState.update {
                baseState.copy(
                    runtime = runtimeRepo.record,
                    huskyRelease = it.huskyRelease,
                    huskyUpdateStatus = it.huskyUpdateStatus,
                    huskyError = it.huskyError,
                    huskyDiagnostic = it.huskyDiagnostic,
                    latestVersionInfo = it.latestVersionInfo,
                    isAbDevice = abDevice,
                    envReport = it.envReport,
                    envOverall = it.envOverall,
                    envSummaryLine = it.envSummaryLine,
                    envChecking = it.envChecking,
                )
            }
            if (baseState.checkUpdateEnabled && _uiState.value.huskyUpdateStatus == HuskyUpdateStatus.Idle) {
                checkHuskyRelease()
            }
        }
    }

    private var envGeneration = 0
    private var envJob: kotlinx.coroutines.Job? = null
    private fun refreshEnvSummary() {
        val generation = ++envGeneration
        envJob?.cancel()
        _uiState.update { it.copy(envChecking = true) }
        envJob = viewModelScope.launch {
          try {
            val result = envCheckRepo.runCheck()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { report ->
                    val summary = report.worstItem?.let { "${it.title}: ${it.detail}" }
                    _uiState.update {
                        it.copy(
                            envReport = report,
                            envOverall = report.overall,
                            envSummaryLine = summary,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            envReport = null,
                            envOverall = EnvCheckSeverity.Unknown,
                            envSummaryLine = error.message,
                        )
                    }
                },
            )
          } finally { if (generation == envGeneration) _uiState.update { it.copy(envChecking = false) } }
        }
    }

    fun checkHuskyUpdate() {
        if (_uiState.value.huskyBusy) return
        viewModelScope.launch { checkHuskyRelease() }
    }

    fun updateLkm() {
        viewModelScope.launch {
            val release = ensureRelease() ?: return@launch
            val asset = selectCurrentAsset(release) ?: return@launch
            _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Downloading) }
            val dest = File(File(ksuApp.filesDir, "husky/${asset.sha256}"), asset.name)
            val result = huskyRepo.downloadToFile(asset.url, dest, asset.sizeBytes, requireNotNull(asset.sha256))
            result.fold(
                onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(
                        ksuApp,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        file,
                    )
                    _flashLkm.tryEmit(uri)
                    _uiState.update {
                        it.copy(
                            huskyRelease = release,
                            huskyUpdateStatus = HuskyUpdateStatus.Available,
                            huskyError = null,
                            huskyDiagnostic = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            huskyUpdateStatus = HuskyUpdateStatus.DownloadError,
                            huskyError = ksuApp.getString(R.string.husky_lkm_download_failed),
                            huskyDiagnostic = error.stackTraceToString().take(16384),
                        )
                    }
                },
            )
        }
    }

    fun downloadLkmToDownloads() {
        viewModelScope.launch {
            val release = ensureRelease() ?: return@launch
            val asset = selectCurrentAsset(release) ?: return@launch
            _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Downloading) }
            val dest = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                asset.name,
            )
            huskyRepo.downloadToFile(asset.url, dest, asset.sizeBytes, requireNotNull(asset.sha256)).fold(
                onSuccess = { file ->
                    _userMessages.tryEmit(ksuApp.getString(R.string.husky_lkm_saved, file.absolutePath))
                    applyRelease(release)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            huskyUpdateStatus = HuskyUpdateStatus.DownloadError,
                            huskyError = ksuApp.getString(R.string.husky_lkm_download_failed),
                            huskyDiagnostic = error.stackTraceToString().take(16384),
                        )
                    }
                },
            )
        }
    }

    fun openHuskyReleaseUrl(): String {
        return _uiState.value.huskyRelease?.htmlUrl ?: HuskyReleaseRepositoryImpl.RELEASES_PAGE
    }

    private suspend fun ensureRelease(): HuskyRelease? {
        val existing = _uiState.value.huskyRelease
        if (!existing?.lkmAssets.isNullOrEmpty()) return existing
        return checkHuskyRelease()
    }

    private suspend fun assessCurrentRelease(release: HuskyRelease): LkmUpdateAssessment = try {
        assessLkmUpdate(release, _uiState.value.ksuVersion?.toLong() ?: 0L,
            LoaderClient.currentKmi().trim(), LoaderClient.currentPageSize(), Os.uname().release)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        LkmUpdateAssessment(LkmUpdateAvailability.DeviceUnknown, diagnostic = error.stackTraceToString().take(16384))
    }

    private suspend fun selectCurrentAsset(release: HuskyRelease): LkmAsset? {
        val assessment = assessCurrentRelease(release)
        if (assessment.candidate == null) applyRelease(release)
        return assessment.candidate
    }

    private suspend fun checkHuskyRelease(): HuskyRelease? {
        _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Checking, huskyError = null, huskyDiagnostic = null) }
        val result = huskyRepo.fetchLatest()
        return result.fold(
            onSuccess = { release ->
                applyRelease(release)
                release
            },
            onFailure = { error ->
                val message = when {
                    error.message == "No husky release" ->
                        ksuApp.getString(R.string.husky_update_no_release)
                    error.message == "Network unavailable" ->
                        ksuApp.getString(R.string.network_offline)
                    error is ReleaseHttpException -> if (error.rateLimited)
                        ksuApp.getString(R.string.husky_update_rate_limited)
                        else ksuApp.getString(R.string.husky_update_http_failed, error.statusCode)
                    else -> ksuApp.getString(R.string.husky_update_request_failed)
                }
                _uiState.update {
                    it.copy(huskyUpdateStatus = HuskyUpdateStatus.Error, huskyError = message,
                        huskyDiagnostic = error.stackTraceToString().take(16384))
                }
                null
            },
        )
    }

    private suspend fun applyRelease(release: HuskyRelease) {
        val current = _uiState.value
        val assessment = assessCurrentRelease(release)
        val apkInfo = release.apkDownloadUrl?.let { apkUrl ->
            LatestVersionInfo(
                versionCode = release.versionCode.toInt().coerceAtLeast(0),
                downloadUrl = apkUrl,
                changelog = release.body,
            )
        } ?: current.latestVersionInfo

        _uiState.update {
            it.copy(
                huskyRelease = release,
                huskyUpdateStatus = when (assessment.availability) {
                    LkmUpdateAvailability.Available -> HuskyUpdateStatus.Available
                    LkmUpdateAvailability.UpToDate -> HuskyUpdateStatus.UpToDate
                    LkmUpdateAvailability.NoMatchingTarget -> HuskyUpdateStatus.NoMatchingLkm
                    LkmUpdateAvailability.MetadataRejected -> HuskyUpdateStatus.MetadataError
                    LkmUpdateAvailability.DeviceUnknown -> HuskyUpdateStatus.Error
                },
                huskyError = when (assessment.availability) {
                    LkmUpdateAvailability.NoMatchingTarget -> ksuApp.getString(R.string.husky_lkm_no_published_target)
                    LkmUpdateAvailability.MetadataRejected -> release.lkmMetadataError ?: ksuApp.getString(R.string.husky_lkm_metadata_unavailable)
                    LkmUpdateAvailability.DeviceUnknown -> ksuApp.getString(R.string.husky_update_device_unknown)
                    else -> null
                },
                huskyDiagnostic = assessment.diagnostic,
                latestVersionInfo = apkInfo,
            )
        }
    }

    private fun buildState(checkRoot: Boolean = true): HomeUiState {
        val kernelVersion = getKernelVersion()
        val isManager = Natives.isManager
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
        val managerUAPIVersion = Natives.managerUAPIVersion
        val lkmMode = ksuVersion?.let { Natives.isLkmMode }
        val isRootAvailable = checkRoot && rootAvailable()
        val managerVersion = getManagerVersion(ksuApp)

        return HomeUiState(
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion,
            lkmMode = lkmMode,
            isManager = isManager,
            isManagerPrBuild = BuildConfig.IS_PR_BUILD,
            isKernelPrBuild = Natives.isPrBuild,
            requiresNewKernel = isManager && Natives.requireNewKernel(),
            uapiMismatch = isManager && Natives.checkUAPIMismatch(),
            kernelUAPIVersion = kernelUAPIVersion,
            managerUAPIVersion = managerUAPIVersion,
            isRootAvailable = isRootAvailable,
            isSafeMode = Natives.isSafeMode,
            isLateLoadMode = Natives.isLateLoadMode,
            checkUpdateEnabled = settingsRepo.checkUpdate,
            latestVersionInfo = LatestVersionInfo(),
            currentManagerVersionCode = managerVersion.versionCode,
            systemInfo = SystemInfo(
                kernelVersion = Os.uname().release,
                managerVersion = "${managerVersion.versionName} (${managerVersion.versionCode}-${managerUAPIVersion})",
                deviceModel = resolveDeviceName(),
                fingerprint = Build.FINGERPRINT,
                selinuxStatus = if (checkRoot) getSELinuxStatusRaw() else "",
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
            ),
        )
    }
}
