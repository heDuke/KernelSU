package me.weishu.kernelsu.ui.component.module

import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.ModuleCompatibility
import me.weishu.kernelsu.data.model.moduleCompatibility
import me.weishu.kernelsu.ui.screen.modulerepo.RepoModuleArg
import me.weishu.kernelsu.ui.util.DownloadManager
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.util.reboot

@Composable
fun ModuleSheetHost(onViewDetails: (RepoModuleArg) -> Unit) {
    val operation by ModuleInstallController.state.collectAsStateWithLifecycle()
    val mutationBusy by me.weishu.kernelsu.core.utils.ModuleMutationGate.busy.collectAsStateWithLifecycle()
    val preview by ModuleInstallController.previewOverlay.collectAsStateWithLifecycle()
    val state = preview?.let { ModuleSheetState(visible = true, preview = it) } ?: operation
    if (!state.visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var acknowledged by remember(state.targets) { mutableStateOf(false) }
    var expandedIntroduction by remember(state.preview) { mutableStateOf(false) }
    var expandedImage by remember(state.preview) { mutableStateOf<String?>(null) }
    val downloads by DownloadManager.downloads.collectAsStateWithLifecycle()
    val module = state.preview
    val title = when (state.stage) {
        ModuleSheetStage.PREVIEW -> stringResource(R.string.module_sheet_preview)
        ModuleSheetStage.REVIEW -> stringResource(R.string.module_sheet_confirm)
        else -> stageLabel(state.stage)
    }
    fun close() { scope.launch { sheetState.hide(); ModuleInstallController.dismiss() } }
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = ModuleInstallController::dismiss,
    ) {
        androidx.activity.compose.BackHandler(enabled = state.stage == ModuleSheetStage.REVIEW && state.fromPreview) {
            ModuleInstallController.back()
        }
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                    val text = buildList {
                        module?.let {
                            add(it.moduleName)
                            if (it.latestRelease.isNotBlank()) add(it.latestRelease)
                            if (it.sourceUrl.isNotBlank()) add(it.sourceUrl)
                        } ?: state.targets.forEach { target ->
                            add(listOf(target.name, target.version).filter(String::isNotBlank).joinToString("\n"))
                        }
                    }.joinToString("\n")
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, null))
                }) { Icon(Icons.Outlined.Share, stringResource(R.string.module_sheet_share)) }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = ::close) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (module != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModuleArtwork(module.iconUrl, null, Modifier.size(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(module.moduleName.ifBlank { module.moduleId }, style = MaterialTheme.typography.headlineSmall)
                                Text(module.authors, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (state.stage == ModuleSheetStage.PREVIEW && module != null) {
                    item {
                        if (operation.busy) Text(stringResource(R.string.module_sheet_task_busy))
                        Text(listOf(module.latestRelease, module.sourceName).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium)
                        if (module.installedVersion.isNotBlank()) Text(stringResource(R.string.module_sheet_installed_version, module.installedVersion))
                        Text(sizeLabel(module.releases.firstOrNull()?.assets?.firstOrNull()?.size ?: 0))
                        CompatibilityText(module.minApi, module.maxApi, module.kernelSuUnsupported)
                        if (module.metamodule) Text("META", style = MaterialTheme.typography.labelLarge)
                    }
                    if (module.screenshots.isNotEmpty()) {
                        item {
                            if (expandedImage != null) {
                                ModuleArtwork(expandedImage!!, stringResource(R.string.module_sheet_screenshot, 1),
                                    Modifier.fillMaxWidth().height(260.dp), screenshot = true)
                                TextButton(shapes = ButtonDefaults.shapes(), onClick = { expandedImage = null }) { Text(stringResource(R.string.module_sheet_overview)) }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(module.screenshots) { index, image ->
                                        ModuleArtwork(image, stringResource(R.string.module_sheet_screenshot, index + 1),
                                            Modifier.width(160.dp).height(180.dp).clickable { expandedImage = image }, screenshot = true)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(module.summary.ifBlank { stringResource(R.string.module_sheet_no_introduction) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = if (expandedIntroduction) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis)
                        if (module.notes.isNotBlank()) Text(module.notes, style = MaterialTheme.typography.bodyMedium)
                        if (module.summary.length > 180) {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { expandedIntroduction = !expandedIntroduction }) {
                                Text(stringResource(if (expandedIntroduction) R.string.module_sheet_less else R.string.module_sheet_more))
                            }
                        }
                    }
                } else if (state.stage == ModuleSheetStage.REVIEW) {
                    if (!module?.notes.isNullOrBlank()) item { Text(module!!.notes, style = MaterialTheme.typography.bodyLarge) }
                    if (module?.dependencies?.isNotEmpty() == true) item {
                        Text(stringResource(R.string.module_sheet_dependencies) + "\n" + module.dependencies.joinToString("\n"))
                    }
                    itemsIndexed(state.targets) { _, target ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(target.name, style = MaterialTheme.typography.titleMedium)
                            if (target.version.isNotBlank()) Text(target.version)
                            Text(target.source.ifBlank { stringResource(R.string.module_sheet_local) }, style = MaterialTheme.typography.bodyMedium)
                            Text(sizeLabel(target.size))
                            CompatibilityText(target.minApi, target.maxApi, target.kernelSuUnsupported)
                        }
                    }
                    item {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (mutationBusy) Text(stringResource(R.string.dashboard_module_busy))
                        Text(stringResource(R.string.module_sheet_review_notice), style = MaterialTheme.typography.bodyMedium)
                        if (!Natives.isManager || Natives.isSafeMode) {
                            Text(stringResource(R.string.module_sheet_root_unavailable), color = MaterialTheme.colorScheme.error)
                        }
                        if (state.incompatible) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                                Text(stringResource(R.string.module_sheet_acknowledge), Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    item {
                        if (state.stage == ModuleSheetStage.DOWNLOADING) {
                            val task = downloads.values.lastOrNull { download -> state.targets.any { it.downloadUrl == download.url } }
                            if (task != null && task.totalBytes > 0) {
                                LinearWavyProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("${task.progress}% · ${Formatter.formatFileSize(context, task.downloadedBytes)} / ${Formatter.formatFileSize(context, task.totalBytes)}")
                                Text(stringResource(R.string.module_sheet_speed, Formatter.formatFileSize(context, task.bytesPerSecond)))
                            } else {
                                LoadingIndicator()
                                task?.let {
                                    Text(Formatter.formatFileSize(context, it.downloadedBytes))
                                    Text(stringResource(R.string.module_sheet_speed, Formatter.formatFileSize(context, it.bytesPerSecond)))
                                }
                            }
                        } else if (state.busy) LoadingIndicator()
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (state.restartRequired) Text(stringResource(R.string.module_sheet_restart_required))
                    }
                    if (state.log.isNotBlank()) item {
                        SelectionContainer { Text(state.log, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.stage) {
                    ModuleSheetStage.PREVIEW -> {
                        Button(shapes = ButtonDefaults.shapes(), onClick = ModuleInstallController::reviewPreview,
                            enabled = module?.releases?.firstOrNull()?.assets?.firstOrNull()?.downloadUrl?.isNotBlank() == true && !operation.busy && !mutationBusy && !module.kernelSuUnsupported && Natives.isManager && !Natives.isSafeMode,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(when {
                            module?.installedVersion?.isNotBlank() == true && module.installedVersion == module.latestRelease -> R.string.module_sheet_reinstall
                            module?.installedVersionCode?.let { it > 0 && module.latestVersionCode > it } == true -> R.string.module_update
                            else -> R.string.install
                        })) }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                            module?.let { selected ->
                                scope.launch { sheetState.hide(); ModuleInstallController.dismiss(); onViewDetails(selected) }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.module_sheet_details)) }
                    }
                    ModuleSheetStage.REVIEW -> Button(shapes = ButtonDefaults.shapes(), onClick = { ModuleInstallController.confirm(acknowledged) },
                        enabled = !mutationBusy && Natives.isManager && !Natives.isSafeMode && state.targets.none { it.kernelSuUnsupported } && (!state.incompatible || acknowledged),
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.module_sheet_confirm)) }
                    else -> if (state.restartRequired && !state.busy) {
                        Button(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { withContext(Dispatchers.IO) {
                            reboot(if (isSoftRebootPreferred()) "soft_reboot" else "")
                        } } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reboot)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleTaskStatus() {
    val state by ModuleInstallController.state.collectAsStateWithLifecycle()
    if (!state.visible && state.stage !in setOf(ModuleSheetStage.PREVIEW, ModuleSheetStage.REVIEW)) {
        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = ModuleInstallController::reopen) { Text(stageLabel(state.stage)) }
    }
}

@Composable
private fun CompatibilityText(minApi: Int?, maxApi: Int?, unsupported: Boolean = false) {
    if (unsupported) { Text(stringResource(R.string.module_sheet_kernel_su_unsupported), color = MaterialTheme.colorScheme.error); return }
    val incompatible = moduleCompatibility(Build.VERSION.SDK_INT, minApi, maxApi) == ModuleCompatibility.INCOMPATIBLE
    Text(stringResource(if (incompatible) R.string.module_sheet_incompatible else R.string.module_sheet_compatibility_unknown),
        color = if (incompatible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
    if (minApi != null || maxApi != null) {
        Text(stringResource(R.string.module_sheet_api_range, minApi?.toString() ?: "?", maxApi?.toString() ?: "?", Build.VERSION.SDK_INT),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun sizeLabel(size: Long): String = if (size > 0) Formatter.formatFileSize(LocalContext.current, size)
    else stringResource(R.string.module_sheet_size_unknown)

@Composable
private fun stageLabel(stage: ModuleSheetStage): String = stringResource(when (stage) {
    ModuleSheetStage.DOWNLOADING -> R.string.module_sheet_downloading
    ModuleSheetStage.VALIDATING -> R.string.module_sheet_validating
    ModuleSheetStage.INSTALLING -> R.string.module_sheet_installing
    ModuleSheetStage.SUCCESS -> R.string.flash_success
    ModuleSheetStage.FAILED -> R.string.flash_failed
    ModuleSheetStage.PREVIEW -> R.string.module_sheet_preview
    ModuleSheetStage.REVIEW -> R.string.module_sheet_confirm
})
