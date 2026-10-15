package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.data.model.EnvCheckSeverity

@Preview(name = "Home compact", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Home large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Home dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDashboardPreview() {
    MaterialExpressiveTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
        HomePagerMaterial(state = HomeUiState(
            kernelVersion = KernelVersion(6, 1, 172), ksuVersion = 32631,
            managerUAPIVersion = 2, kernelUAPIVersion = 2, lkmMode = true,
            isManager = true, isManagerPrBuild = false, isKernelPrBuild = false,
            requiresNewKernel = false, uapiMismatch = false, isRootAvailable = true,
            isSafeMode = false, isLateLoadMode = false, checkUpdateEnabled = false,
            latestVersionInfo = me.weishu.kernelsu.ui.util.module.LatestVersionInfo(),
            currentManagerVersionCode = 32640, loaded = true,
            systemInfo = SystemInfo("6.1.172-android14-11-preview", "Manager preview", "Pixel 8 Pro", "preview", "Enforcing", 2),
            envOverall = EnvCheckSeverity.Unknown, envSummaryLine = "Validation evidence incomplete"),
            actions = HomeActions({}, {}), bottomInnerPadding = 0.dp, active = false)
    }
}
