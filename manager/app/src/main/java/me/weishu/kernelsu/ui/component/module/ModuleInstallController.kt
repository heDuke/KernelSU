package me.weishu.kernelsu.ui.component.module

import me.weishu.kernelsu.ui.util.message
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.ModuleCompatibility
import me.weishu.kernelsu.data.model.moduleCompatibility
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.navigation3.getDisplayName
import me.weishu.kernelsu.ui.screen.modulerepo.RepoModuleArg
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.flashModule
import me.weishu.kernelsu.ui.util.hasMagisk
import java.io.File
import kotlinx.coroutines.CancellationException
import me.weishu.kernelsu.core.utils.*
import me.weishu.kernelsu.R

internal enum class ModuleSheetStage { PREVIEW, REVIEW, DOWNLOADING, VALIDATING, INSTALLING, SUCCESS, FAILED }

internal data class ModuleInstallTarget(
    val name: String,
    val uri: Uri? = null,
    val downloadUrl: String = "",
    val version: String = "",
    val versionCode: Int = 0,
    val source: String = "",
    val moduleId: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val minApi: Int? = null,
    val maxApi: Int? = null,
    val kernelSuUnsupported: Boolean = false,
    val dependencies: List<String> = emptyList(),
)

internal data class ModuleSheetState(
    val visible: Boolean = false,
    val stage: ModuleSheetStage = ModuleSheetStage.PREVIEW,
    val preview: RepoModuleArg? = null,
    val targets: List<ModuleInstallTarget> = emptyList(),
    val fromPreview: Boolean = false,
    val log: String = "",
    val error: String? = null,
    val restartRequired: Boolean = false,
) {
    val busy: Boolean get() = stage in setOf(ModuleSheetStage.DOWNLOADING, ModuleSheetStage.VALIDATING, ModuleSheetStage.INSTALLING)
    val incompatible: Boolean get() = targets.any {
        moduleCompatibility(Build.VERSION.SDK_INT, it.minApi, it.maxApi) == ModuleCompatibility.INCOMPATIBLE
    }
}

/** Process-scoped operation owner: dismissing/recreating a sheet never restarts or cancels an install. */
internal object ModuleInstallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ModuleSheetState())
    val state = mutableState.asStateFlow()
    private val mutablePreview = MutableStateFlow<RepoModuleArg?>(null)
    val previewOverlay = mutablePreview.asStateFlow()
    val completion = MutableStateFlow(0)
    val scriptNavigation = MutableStateFlow<me.weishu.kernelsu.ui.screen.scriptrun.ScriptRunArg?>(null)

    fun preview(module: RepoModuleArg) {
        mutableState.update { it.copy(visible = false) }
        mutablePreview.value = module
    }

    fun review(targets: List<ModuleInstallTarget>, module: RepoModuleArg? = null) {
        if (state.value.busy) { reopen(); return }
        if (targets.isEmpty()) return
        mutablePreview.value = null
        mutableState.value = ModuleSheetState(visible = true, stage = ModuleSheetStage.REVIEW,
            preview = module, targets = targets.toList(), fromPreview = false)
    }

    fun reviewLocal(uris: List<Uri>) {
        if (state.value.busy) { reopen(); return }
        scope.launch {
            if (uris.size == 1) {
                val script = me.weishu.kernelsu.data.script.ScriptRepository.peekOneshot(uris.single())
                if (script != null) {
                    scriptNavigation.value = me.weishu.kernelsu.ui.screen.scriptrun.ScriptRunArg(uris.single(), offerInstall = true, displayName = script.name)
                    return@launch
                }
            }
            review(uris.map { ModuleInstallTarget(name = getDisplayName(it), uri = it) })
        }
    }

    suspend fun prepareScript(module: RepoModuleArg, selected: me.weishu.kernelsu.ui.screen.modulerepo.ReleaseAssetArg? = null) {
        check(module.isScript && module.sourceId == "kernelsu")
        val asset = selected ?: module.releases.first().assets.single()
        check(asset.sha256.isNotBlank()) { "Script verification metadata missing" }
        val uri = download(asset.downloadUrl, asset.name, expected = me.weishu.kernelsu.data.registry.ExpectedFile(asset.size, asset.sha256))
            ?: throw ModuleDownloadException()
        scriptNavigation.value = me.weishu.kernelsu.ui.screen.scriptrun.ScriptRunArg(uri, registryId = module.moduleId, displayName = module.moduleName)
    }

    fun reviewPreview(selected: me.weishu.kernelsu.ui.screen.modulerepo.ReleaseAssetArg? = null) {
        val snapshot = state.value
        if (snapshot.busy) return
        val module = mutablePreview.value ?: snapshot.preview ?: return
        val assets = module.releases.firstOrNull()?.assets.orEmpty()
        val asset = selected?.takeIf { it in assets } ?: assets.singleOrNull() ?: return
        mutablePreview.value = null
        mutableState.value = ModuleSheetState(visible = true, preview = module, stage = ModuleSheetStage.REVIEW, fromPreview = true,
            targets = listOf(ModuleInstallTarget(asset.name, downloadUrl = asset.downloadUrl,
                version = module.latestRelease, source = module.sourceName.ifBlank { module.sourceUrl },
                moduleId = module.moduleId, size = asset.size, sha256 = asset.sha256, minApi = module.minApi, maxApi = module.maxApi,
                kernelSuUnsupported = module.kernelSuUnsupported, dependencies = module.dependencies)))
    }

    fun back() {
        if (state.value.stage == ModuleSheetStage.REVIEW && state.value.fromPreview) {
            mutablePreview.value = state.value.preview
            mutableState.update { it.copy(visible = false, stage = ModuleSheetStage.PREVIEW, targets = emptyList(), fromPreview = false) }
        } else dismiss()
    }

    fun dismiss() {
        if (mutablePreview.value != null) mutablePreview.value = null
        else mutableState.update { it.copy(visible = false) }
    }
    fun reopen() { mutablePreview.value = null; mutableState.update { it.copy(visible = true) } }

    fun confirm(acknowledgeIncompatibility: Boolean) {
        val snapshot = state.value
        if (!snapshot.visible || mutablePreview.value != null || snapshot.stage != ModuleSheetStage.REVIEW || snapshot.targets.isEmpty() || snapshot.targets.any { it.kernelSuUnsupported } ||
            (snapshot.incompatible && !acknowledgeIncompatibility)) return
        if (!me.weishu.kernelsu.core.utils.ModuleMutationGate.tryAcquire()) {
            mutableState.update { it.copy(error = ksuApp.getString(me.weishu.kernelsu.R.string.dashboard_module_busy)) }
            return
        }
        mutableState.value = snapshot.copy(stage = ModuleSheetStage.VALIDATING, error = null, log = "")
        scope.launch {
            var restartRequired = false
            try {
                val gate = me.weishu.kernelsu.core.utils.evaluateRootGate(me.weishu.kernelsu.core.utils.RootOperation.ModuleMutation,
                    me.weishu.kernelsu.ui.util.currentRootGateFacts(checkRoot = false).copy(rootAvailable = true))
                gate.message(ksuApp)?.let { error(it) }
                check(!Natives.checkUAPIMismatch()) { ksuApp.getString(me.weishu.kernelsu.R.string.dashboard_interface_mismatch) }
                check(!withContext(Dispatchers.IO) { hasMagisk() }) { "Remove the conflicting root provider before installing modules." }
                val installed = me.weishu.kernelsu.data.repository.ModuleRepositoryImpl().getModules().getOrThrow().associateBy { it.id }
                val registry = me.weishu.kernelsu.data.repository.OfficialRegistryRepository()
                val provenance = ksuApp.getSharedPreferences("module-provenance", android.content.Context.MODE_PRIVATE)
                val targets = snapshot.targets.map { target ->
                    val previous = installed[target.moduleId]
                    val fromRegistry = previous != null && (registry.isRegistryUrl(previous.updateJson) || provenance.getString(target.moduleId + ".source_id", "") == "kernelsu")
                    if (target.uri == null && target.sha256.isBlank() && fromRegistry) {
                        val verified = registry.resolveUpdate(target.moduleId, previous!!.updateJson, target.downloadUrl, target.versionCode)
                        check(verified is me.weishu.kernelsu.data.registry.UpdateVerification.Registry) { "Registry update could not be verified" }
                        target.copy(downloadUrl = verified.zipUrl, size = verified.expected.size, sha256 = verified.expected.sha256)
                    } else target
                }
                mutableState.update { it.copy(targets = targets) }
                val knownDependencies = targets.flatMap { it.dependencies }
                if (knownDependencies.isNotEmpty()) {
                    val existing = me.weishu.kernelsu.data.repository.ModuleRepositoryImpl().getModules().getOrThrow()
                        .filter { it.enabled && !it.remove }.map { it.id }.toSet()
                    val missing = knownDependencies.filter { it !in existing }
                    check(missing.isEmpty()) { "Install and enable dependencies first: ${missing.joinToString(", ")}" }
                }
                // Validate every selected package before executing any installer in a batch.
                val prepared = ArrayList<File>()
                val metamodules = ArrayList<String>()
                try {
                    for (target in targets) {
                        val uri = target.uri ?: run {
                            mutableState.update { it.copy(stage = ModuleSheetStage.DOWNLOADING) }
                            download(target.downloadUrl, target.name, expected = target.sha256.takeIf { it.isNotBlank() }?.let { me.weishu.kernelsu.data.registry.ExpectedFile(target.size, it) })
                                ?: throw ModuleDownloadException()
                        }
                        mutableState.update { it.copy(stage = ModuleSheetStage.VALIDATING) }
                        val file = withContext(Dispatchers.IO) {
                            File.createTempFile("module-review-", ".zip", ksuApp.cacheDir).also { file ->
                                try {
                                    ksuApp.contentResolver.openInputStream(uri)?.use { input ->
                                        me.weishu.kernelsu.data.registry.copyForInstall(input, file, target.sha256.takeIf { it.isNotBlank() }?.let { me.weishu.kernelsu.data.registry.ExpectedFile(target.size, it) })
                                    } ?: error("Cannot read ${target.name}")
                                    val metadata = me.weishu.kernelsu.core.utils.validateModulePackage(file, target.size, target.moduleId)
                                    if (metadata.metamodule) { check(metadata.id.isNotBlank()) { "Metamodule has no identity" }; metamodules += metadata.id }
                                } catch (error: Exception) { file.delete(); throw error }
                            }
                        }
                        prepared += file
                    }
                    check(metamodules.distinct().size <= 1) { "A batch cannot install different metamodules." }
                    if (metamodules.isNotEmpty()) {
                        val existing = me.weishu.kernelsu.data.repository.ModuleRepositoryImpl().getModules().getOrThrow()
                        check(existing.none { it.metamodule && it.id != metamodules.first() }) {
                            "Another metamodule exists. Remove it and restart before installing a replacement."
                        }
                    }
                    check(Natives.isManager && !Natives.isSafeMode) { "Root state changed before installation." }
                    mutableState.update { it.copy(stage = ModuleSheetStage.INSTALLING) }
                    for ((index, file) in prepared.withIndex()) {
                        appendLog(targets[index].name)
                        val result = withContext(Dispatchers.IO) {
                            flashModule(Uri.fromFile(file), ::appendLog, ::appendLog, targets[index].sha256.takeIf { it.isNotBlank() }?.let { me.weishu.kernelsu.data.registry.ExpectedFile(targets[index].size, it) }).also {
                                if (it.showReboot) me.weishu.kernelsu.data.repository.ModuleRebootStore.markChanged()
                            }
                        }
                        restartRequired = restartRequired || result.showReboot
                        check(result.code == 0) { result.err.ifBlank { "Installer exited with code ${result.code}" } }
                        val target = targets[index]
                        if (target.moduleId.isNotBlank() && snapshot.preview?.sourceId != null && snapshot.preview.sourceId != "legacy") {
                            ksuApp.getSharedPreferences("module-provenance", android.content.Context.MODE_PRIVATE).edit()
                                .putString(target.moduleId + ".source", snapshot.preview.sourceName)
                                .putString(target.moduleId + ".source_id", snapshot.preview.sourceId).apply()
                        }
                    }
                    mutableState.update { it.copy(stage = ModuleSheetStage.SUCCESS, restartRequired = restartRequired) }
                } finally { prepared.forEach(File::delete) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                android.util.Log.e("KernelSU", "Module installation failed", error)
                val message = when (classifyModuleInstallFailure(error)) {
                    ModuleInstallFailure.UnsupportedCompression -> {
                        val unsupported = error as UnsupportedZipMethodException
                        ksuApp.getString(R.string.module_zip_unsupported_method,
                            zipMethodName(unsupported.method), unsupported.method,
                            KSUD_SUPPORTED_ZIP_METHODS.values.joinToString(", "))
                    }
                    ModuleInstallFailure.InvalidPackage -> ksuApp.getString(when ((error as ModulePackageException).reason) {
                        ModulePackageFailure.SizeMismatch -> R.string.module_zip_size_mismatch
                        ModulePackageFailure.IdentityMismatch, ModulePackageFailure.IdentityMissing -> R.string.module_zip_identity_mismatch
                        ModulePackageFailure.MetadataTooLarge -> R.string.module_zip_metadata_large
                        ModulePackageFailure.CrcMismatch -> R.string.module_zip_crc_mismatch
                        ModulePackageFailure.InvalidArchive -> R.string.module_zip_invalid
                    })
                    ModuleInstallFailure.ReadWrite -> ksuApp.getString(R.string.module_install_io_failed)
                    ModuleInstallFailure.Permission -> ksuApp.getString(R.string.module_install_permission_failed)
                    ModuleInstallFailure.Download -> ksuApp.getString(R.string.module_install_download_failed)
                    ModuleInstallFailure.Other -> ksuApp.getString(R.string.module_install_failed_detail)
                }
                appendLog(message + "\n" + error.stackTraceToString())
                mutableState.update { it.copy(stage = ModuleSheetStage.FAILED,
                    error = message, restartRequired = restartRequired) }
            } finally {
                me.weishu.kernelsu.core.utils.ModuleMutationGate.release()
                completion.value += 1
            }
        }
    }

    private fun appendLog(line: String) {
        mutableState.update { it.copy(log = (it.log + line + "\n").takeLast(65536)) }
    }
}
