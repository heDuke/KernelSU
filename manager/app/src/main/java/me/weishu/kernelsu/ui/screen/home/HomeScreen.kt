package me.weishu.kernelsu.ui.screen.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    if (hasActivated) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
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
            val partition = withContext(Dispatchers.IO) {
                getDefaultPartition().ifBlank { null }
            }
            navigator.push(
                Route.Flash(
                    FlashIt.FlashBoot(
                        boot = null,
                        lkm = LkmSelection.LkmUri(uri),
                        ota = false,
                        partition = partition,
                        allowShell = true,
                    )
                )
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val actions = HomeActions(
        onInstallClick = { navigator.push(Route.Install) },
        onOpenUrl = uriHandler::openUri,
        onCheckHuskyUpdate = viewModel::checkHuskyUpdate,
        onUpdateLkm = viewModel::updateLkm,
        onDownloadLkmToDownloads = viewModel::downloadLkmToDownloads,
        onOpenHuskyRelease = { uriHandler.openUri(viewModel.openHuskyReleaseUrl()) },
    )

    HomePagerMaterial(
        state = uiState,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
    )
}
