package me.weishu.kernelsu.ui.screen.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.LkmSelection
import me.weishu.kernelsu.ui.util.getDefaultPartition
import me.weishu.kernelsu.ui.viewmodel.HomeViewModel

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()

    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleState by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val completed by ModuleInstallController.completion.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val pager = LocalMainPagerState.current
    val scope = rememberCoroutineScope()
    val batchBusy by me.weishu.kernelsu.core.utils.ModuleMutationGate.busy.collectAsStateWithLifecycle()
    val modulePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) ModuleInstallController.reviewLocal(uris)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val state = viewModel.uiState.value
            val report = buildString {
                appendLine("HuskySU home diagnostics")
                appendLine("manager=${state.systemInfo.managerVersion}")
                appendLine("kernel=${state.systemInfo.kernelVersion} driver=${state.ksuVersion} mode=${state.lkmMode}")
                appendLine("device=${state.systemInfo.deviceModel} page_size=${state.pageSize} slot=${state.slot}")
                appendLine("manager_authorized=${state.isManager} root=${state.isRootAvailable} safe_mode=${state.isSafeMode}")
                appendLine("observed_incidents=${state.runtime.total} record_error=${state.runtimeError}")
                state.runtime.events.forEach { appendLine("${it.at} ${it.code}") }
                appendLine(state.envReport?.toPlainReport().orEmpty())
                appendLine("load_attempted=false; observation report is not a load/boot/recovery proof")
            }
            val result = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) }
            } }
            Toast.makeText(context, result.exceptionOrNull()?.message ?: context.getString(R.string.dashboard_export), Toast.LENGTH_LONG).show()
        }
    }
    fun batch(restore: Boolean) {
        if (batchBusy) return
        scope.launch {
            try {
                val result = HomeModuleBatch.run(restore)
                Toast.makeText(context, result.fold({ if (it == 0) context.getString(R.string.dashboard_no_todos) else "$it · " + context.getString(R.string.reboot_to_apply) }, { it.message }), Toast.LENGTH_LONG).show()
                moduleViewModel.fetchModuleList(checkUpdate = true)
            } finally { moduleViewModel.markNeedRefresh() }
        }
    }
    LaunchedEffect(isCurrentPage, lifecycle) {
        if (!isCurrentPage) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.resumeObservation()
            try {
                var initial = true
                while (true) {
                    viewModel.refresh(includeEnvironment = initial)
                    initial = false
                    delay(30_000)
                }
            } finally { viewModel.pauseObservation() }
        }
    }
    LaunchedEffect(isCurrentPage, uiState.isFullFeatured, completed) {
        if (isCurrentPage && uiState.isFullFeatured) {
            moduleViewModel.initializePreferences()
            moduleViewModel.fetchModuleList(checkUpdate = true)
        }
    }

    var huskyDownloadShown by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.huskyUpdateStatus) {
        val downloading = uiState.huskyUpdateStatus == HuskyUpdateStatus.Downloading
        if (downloading && !huskyDownloadShown) {
            loadingDialog.showLoading()
            huskyDownloadShown = true
        } else if (!downloading && huskyDownloadShown) {
            loadingDialog.hide()
            huskyDownloadShown = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.flashLkm.collect { uri ->
            navigator.push(Route.InstallLkm(uri.toString()))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val alignConfirmTitle = stringResource(R.string.husky_update_lkm)
    val alignConfirmText = stringResource(
        R.string.husky_update_confirm,
        uiState.huskyRelease?.tag ?: "",
    )
    val alignDialog = rememberConfirmDialog(onConfirm = viewModel::updateLkm)

    val actions = HomeActions(
        onInstallClick = { navigator.push(Route.Install) },
        onOpenUrl = uriHandler::openUri,
        onRefresh = { viewModel.refresh(); if (uiState.isFullFeatured) moduleViewModel.fetchModuleList(checkUpdate = true) },
        onCheckInputs = { navigator.push(Route.InstallCheck) },
        onInstallModule = { modulePicker.launch("application/zip") },
        onOpenModules = { pager.animateToPage(2) },
        onOpenLoader = { navigator.push(Route.ExperimentalLoaderSettings) },
        onExportReport = { export.launch("HuskySU-diagnostics-${System.currentTimeMillis()}.txt") },
        onReboot = { scope.launch(Dispatchers.IO) {
            if (me.weishu.kernelsu.Natives.isManager && me.weishu.kernelsu.ui.util.rootAvailable()) me.weishu.kernelsu.ui.util.reboot()
        } },
        onDisableModules = { batch(false) },
        onRestoreModules = { batch(true) },
        onCheckHuskyUpdate = viewModel::checkHuskyUpdate,
        onUpdateLkm = viewModel::updateLkm,
        onDownloadLkmToDownloads = viewModel::downloadLkmToDownloads,
        onOpenHuskyRelease = { uriHandler.openUri(viewModel.openHuskyReleaseUrl()) },
        onOpenEnvCheck = { navigator.push(Route.EnvCheck) },
        onAlignEnvironment = {
            if (uiState.envAlignAvailable) {
                alignDialog.showConfirm(
                    title = alignConfirmTitle,
                    content = alignConfirmText,
                    confirm = alignConfirmTitle,
                )
            } else {
                viewModel.checkHuskyUpdate()
            }
        },
        onInstallInactiveSlot = { navigator.push(Route.InstallOta) },
    )

    HomePagerMaterial(
        state = uiState.copy(moduleBatchBusy = batchBusy),
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
        modules = moduleState,
        active = isCurrentPage,
    )
}
