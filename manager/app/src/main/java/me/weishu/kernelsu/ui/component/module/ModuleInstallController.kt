package me.weishu.kernelsu.ui.component.module

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

internal enum class ModuleSheetStage { PREVIEW, REVIEW, DOWNLOADING, VALIDATING, INSTALLING, SUCCESS, FAILED }

internal data class ModuleInstallTarget(
    val name: String,
    val uri: Uri? = null,
    val downloadUrl: String = "",
    val version: String = "",
    val source: String = "",
    val moduleId: String = "",
    val size: Long = 0,
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

    fun reviewLocal(uris: List<Uri>) = review(uris.map {
        ModuleInstallTarget(name = getDisplayName(it), uri = it)
    })

    fun reviewPreview() {
        val snapshot = state.value
        if (snapshot.busy) return
        val module = mutablePreview.value ?: snapshot.preview ?: return
        val asset = module.releases.firstOrNull()?.assets?.firstOrNull() ?: return
        mutablePreview.value = null
        mutableState.value = ModuleSheetState(visible = true, preview = module, stage = ModuleSheetStage.REVIEW, fromPreview = true,
            targets = listOf(ModuleInstallTarget(asset.name, downloadUrl = asset.downloadUrl,
                version = module.latestRelease, source = module.sourceName.ifBlank { module.sourceUrl },
                moduleId = module.moduleId, size = asset.size, minApi = module.minApi, maxApi = module.maxApi,
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
                check(Natives.isManager && !Natives.isSafeMode) { "Module installation is unavailable in this root state." }
                check(!withContext(Dispatchers.IO) { hasMagisk() }) { "Remove the conflicting root provider before installing modules." }
                val knownDependencies = snapshot.targets.flatMap { it.dependencies }
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
                    for (target in snapshot.targets) {
                        val uri = target.uri ?: run {
                            mutableState.update { it.copy(stage = ModuleSheetStage.DOWNLOADING) }
                            download(target.downloadUrl, target.name)
                                ?: error("Download failed: ${target.name}")
                        }
                        mutableState.update { it.copy(stage = ModuleSheetStage.VALIDATING) }
                        val file = withContext(Dispatchers.IO) {
                            File.createTempFile("module-review-", ".zip", ksuApp.cacheDir).also { file ->
                                try {
                                    ksuApp.contentResolver.openInputStream(uri)?.use { input ->
                                        file.outputStream().use { output -> input.copyTo(output) }
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
                        appendLog(snapshot.targets[index].name)
                        val result = withContext(Dispatchers.IO) {
                            flashModule(Uri.fromFile(file), ::appendLog, ::appendLog)
                        }
                        restartRequired = restartRequired || result.showReboot || result.code == 0
                        check(result.code == 0) { result.err.ifBlank { "Installer exited with code ${result.code}" } }
                        val target = snapshot.targets[index]
                        if (target.moduleId.isNotBlank() && snapshot.preview?.sourceId != null && snapshot.preview.sourceId != "legacy") {
                            ksuApp.getSharedPreferences("module-provenance", android.content.Context.MODE_PRIVATE).edit()
                                .putString(target.moduleId + ".source", snapshot.preview.sourceName)
                                .putString(target.moduleId + ".source_id", snapshot.preview.sourceId).apply()
                        }
                    }
                    mutableState.update { it.copy(stage = ModuleSheetStage.SUCCESS, restartRequired = restartRequired) }
                } finally { prepared.forEach(File::delete) }
            } catch (error: Exception) {
                mutableState.update { it.copy(stage = ModuleSheetStage.FAILED,
                    error = error.message ?: error.javaClass.simpleName, restartRequired = restartRequired) }
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
