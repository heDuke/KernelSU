package me.weishu.kernelsu.ui.screen.envcheck

import android.content.ClipData
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.EnvCheckViewModel

@Composable
fun EnvCheckScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<EnvCheckViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = EnvCheckScreenActions(
        onBack = { navigator.pop() },
        onRefresh = viewModel::refresh,
        onCopyReport = { report ->
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("HuskySU env check", report.toPlainReport()))
                )
                Toast.makeText(context, context.getString(R.string.env_check_copied), Toast.LENGTH_SHORT).show()
            }
        },
    )

    EnvCheckScreenMaterial(
        isLoading = uiState.isLoading,
        report = uiState.report,
        error = uiState.error,
        actions = actions,
    )
}
