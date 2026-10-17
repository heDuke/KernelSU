package me.weishu.kernelsu.ui.screen.module

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import me.weishu.kernelsu.ui.component.module.ModuleInstallTarget
import me.weishu.kernelsu.ui.screen.modulerepo.RepoModuleArg
import me.weishu.kernelsu.ui.screen.modulerepo.ReleaseArg
import me.weishu.kernelsu.ui.screen.modulerepo.ReleaseAssetArg
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.module.Shortcut
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import me.weishu.kernelsu.ui.webui.WebUIActivity

@Composable
fun ModulePager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val resource = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = viewModel<ModuleViewModel>()
    val scope = rememberCoroutineScope()
    val rawUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val completed by ModuleInstallController.completion.collectAsStateWithLifecycle()
    LaunchedEffect(completed) {
        if (completed > 0) { viewModel.markNeedRefresh(); viewModel.fetchModuleList() }
    }

    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.fetchModuleList(resort = false) }

    // Request notification permission for download progress notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Download works regardless of result */ }

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    if (hasActivated) {
        LaunchedEffect(Unit) {
            viewModel.refreshEnvironmentState()
            viewModel.initializePreferences()
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LifecycleResumeEffect(Unit) {
            viewModel.fetchModuleList(
                checkUpdate = rawUiState.moduleList.isEmpty() || viewModel.isNeedRefresh,
                resort = rawUiState.moduleList.isEmpty(),
            )
            onPauseOrDispose {}
        }
    }

    val actions = ModuleActions(
        onRefresh = {
            viewModel.fetchModuleList(checkUpdate = true)
        },
        onSearchStatusChange = {
            viewModel.updateSearchStatus(it)
        },
        onSearchTextChange = { text ->
            viewModel.updateSearchText(text)
        },
        onClearSearch = {
            viewModel.updateSearchText("")
        },
        onRequestUpdateConfirmation = { module, updateInfo ->
            ModuleInstallController.review(listOf(ModuleInstallTarget(
                name = "${module.name}-${updateInfo.version}.zip", downloadUrl = updateInfo.downloadUrl,
                version = updateInfo.version, versionCode = updateInfo.versionCode, source = module.updateJson, moduleId = module.id,
            )))
        },
        onRequestUninstallConfirmation = { module ->
            viewModel.requestUninstallConfirmation(module)
        },
        onDismissConfirmRequest = {
            viewModel.dismissConfirmRequest()
        },
        onConfirmUpdate = { request ->
            ModuleInstallController.review(listOf(ModuleInstallTarget(
                request.fileName, downloadUrl = request.downloadUrl, moduleId = request.module.id,
            )))
            viewModel.dismissConfirmRequest()
        },
        onToggleSortActionFirst = {
            viewModel.toggleSortActionFirst()
        },
        onToggleSortEnabledFirst = {
            viewModel.toggleSortEnabledFirst()
        },
        onOpenModuleDetail = { module ->
            val update = rawUiState.updateInfo[module.id]
            navigator.push(Route.ModuleRepoDetail(RepoModuleArg(module.id, module.name, module.author,
                emptyList(), module.version, "", if (update?.downloadUrl?.isNotBlank() == true) {
                    listOf(ReleaseArg(update.version, update.version, "",
                        listOf(ReleaseAssetArg("${module.name}-${update.version}.zip", update.downloadUrl, 0, 0)), update.changelog))
                } else emptyList(), summary = module.description,
                installedVersion = module.version, latestVersionCode = update?.versionCode ?: 0,
                sourceName = context.getSharedPreferences("module-provenance", android.content.Context.MODE_PRIVATE)
                    .getString(module.id + ".source", null) ?: "")))
        },
        onOpenWebUi = { module ->
            webUILauncher.launch(
                Intent(context, WebUIActivity::class.java)
                    .setData(
                        Shortcut.buildShortcutUri(module.id, ShortcutType.WebUI)
                    )
            )
        },
        onToggleModule = { module ->
            viewModel.toggleModule(module)
        },
        onUninstallModule = { module ->
            viewModel.uninstallModule(module)
        },
        onUndoUninstallModule = { module ->
            viewModel.undoUninstallModule(module)
        },
        onOpenFlash = { uris ->
            if (uris.isNotEmpty()) {
                navigator.push(Route.Flash(FlashIt.FlashModules(uris)))
                viewModel.markNeedRefresh()
            }
        },
        onExecuteModuleAction = { module ->
            navigator.push(Route.ExecuteModuleAction(module.id))
            viewModel.markNeedRefresh()
        },
        onOpenRecommendedHomepage = { module ->
            if (module.homepage.isNotBlank()) {
                uriHandler.openUri(module.homepage)
            }
        },
        onInstallRecommended = { module ->
            ModuleInstallController.preview(RepoModuleArg(module.id, module.name, "", emptyList(), "", "",
                listOf(ReleaseArg("", "", "", listOf(ReleaseAssetArg(module.zipFileName, module.downloadUrl, 0, 0)), "")),
                summary = listOf(module.description, module.note).filter(String::isNotBlank).joinToString("\n"),
                sourceUrl = module.homepage))
        },
        onOpenModuleRepo = { navigator.push(Route.ModuleRepo) },
        onTogglePendingSelection = { id ->
            viewModel.togglePendingSelection(id)
        },
        onProcessSelectedPending = {
            val snapshot = viewModel.uiState.value
            if (snapshot.installButtonVisible) {
                ModuleInstallController.review(snapshot.selectedPendingItems.map {
                    ModuleInstallTarget(it.fileName, downloadUrl = it.downloadUrl, moduleId = it.id, versionCode = snapshot.updateInfo[it.id]?.versionCode ?: 0)
                })
            }
        },
    )

    ModulePagerMaterial(
        uiState = rawUiState,
        confirmDialogState = rawUiState.confirmDialogState,
        moduleEvent = viewModel.moduleEvent,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
    )
}
