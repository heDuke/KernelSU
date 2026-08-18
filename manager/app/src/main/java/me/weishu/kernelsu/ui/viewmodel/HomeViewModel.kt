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
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.HuskyRelease
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
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.resolveDeviceName
import me.weishu.kernelsu.ui.util.rootAvailable
import java.io.File

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl(),
    private val huskyRepo: HuskyReleaseRepository = HuskyReleaseRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _flashLkm = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val flashLkm: SharedFlow<Uri> = _flashLkm.asSharedFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            val previous = _uiState.value
            val (baseState, abDevice) = withContext(Dispatchers.IO) {
                buildState() to isAbDevice()
            }
            _uiState.update {
                baseState.copy(
                    huskyRelease = previous.huskyRelease,
                    huskyUpdateStatus = previous.huskyUpdateStatus,
                    huskyError = previous.huskyError,
                    latestVersionInfo = previous.latestVersionInfo,
                    isAbDevice = abDevice,
                )
            }
            if (baseState.checkUpdateEnabled && previous.huskyRelease == null) {
                checkHuskyRelease()
            }
        }
    }

    fun checkHuskyUpdate() {
        viewModelScope.launch { checkHuskyRelease() }
    }

    fun updateLkm() {
        viewModelScope.launch {
            val release = ensureRelease() ?: return@launch
            val url = release.lkmDownloadUrl
            if (url.isNullOrEmpty()) {
                _uiState.update {
                    it.copy(
                        huskyUpdateStatus = HuskyUpdateStatus.Error,
                        huskyError = ksuApp.getString(R.string.husky_update_no_release),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Downloading) }
            val dest = File(File(ksuApp.filesDir, "husky"), HuskyReleaseRepositoryImpl.LKM_ASSET)
            val result = huskyRepo.downloadToFile(url, dest)
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
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            huskyUpdateStatus = HuskyUpdateStatus.Error,
                            huskyError = error.message ?: error.toString(),
                        )
                    }
                },
            )
        }
    }

    fun downloadLkmToDownloads() {
        viewModelScope.launch {
            val release = ensureRelease() ?: return@launch
            val url = release.lkmDownloadUrl
            if (url.isNullOrEmpty()) {
                _uiState.update {
                    it.copy(
                        huskyUpdateStatus = HuskyUpdateStatus.Error,
                        huskyError = ksuApp.getString(R.string.husky_update_no_release),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Downloading) }
            val dest = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                HuskyReleaseRepositoryImpl.LKM_ASSET,
            )
            huskyRepo.downloadToFile(url, dest).fold(
                onSuccess = { file ->
                    _userMessages.tryEmit(ksuApp.getString(R.string.husky_lkm_saved, file.absolutePath))
                    applyRelease(release)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            huskyUpdateStatus = HuskyUpdateStatus.Error,
                            huskyError = error.message ?: error.toString(),
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
        if (existing?.lkmDownloadUrl != null) return existing
        return checkHuskyRelease()
    }

    private suspend fun checkHuskyRelease(): HuskyRelease? {
        _uiState.update { it.copy(huskyUpdateStatus = HuskyUpdateStatus.Checking, huskyError = null) }
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
                    else ->
                        ksuApp.getString(
                            R.string.husky_update_check_failed,
                            error.message ?: error.toString(),
                        )
                }
                _uiState.update {
                    it.copy(huskyUpdateStatus = HuskyUpdateStatus.Error, huskyError = message)
                }
                null
            },
        )
    }

    private fun applyRelease(release: HuskyRelease) {
        val current = _uiState.value
        val installed = current.ksuVersion?.toLong() ?: 0L
        val hasLkmUpdate = release.versionCode == 0L || release.versionCode > installed
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
                huskyUpdateStatus = if (hasLkmUpdate) {
                    HuskyUpdateStatus.Available
                } else {
                    HuskyUpdateStatus.UpToDate
                },
                huskyError = null,
                latestVersionInfo = apkInfo,
            )
        }
    }

    private fun buildState(): HomeUiState {
        val kernelVersion = getKernelVersion()
        val isManager = Natives.isManager
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
        val managerUAPIVersion = Natives.managerUAPIVersion
        val lkmMode = ksuVersion?.let { if (kernelVersion.isGKI()) Natives.isLkmMode else null }
        val isRootAvailable = rootAvailable()
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
                selinuxStatus = getSELinuxStatusRaw(),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
            ),
        )
    }
}
