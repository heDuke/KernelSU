package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.ui.screen.module.ModuleUiState
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

private fun example() = HomeUiState(KernelVersion(6, 1, 172), 32631, 2, 2, true,
    true, false, false, false, false, true, false, false, false,
    LatestVersionInfo(), 32642, SystemInfo("6.1.172-android14-11-example", "HuskySU example", "Google Pixel 8 Pro", "example", "Enforcing", 2), loaded = true, observedAt = android.os.SystemClock.elapsedRealtime())

@Composable
private fun DesignSample(panel: HomePanel, state: HomeUiState) {
    MaterialExpressiveTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeDetailContent(panel, state, HomeActions({}, {}), ModuleUiState(), emptyList(), false, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Activation healthy", widthDp = 393, heightDp = 760, showBackground = true)
@Preview(name = "Activation healthy dark", widthDp = 393, heightDp = 760, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable private fun HealthySample() = DesignSample(HomePanel.Status, example())

@Preview(name = "Activation unknown", widthDp = 393, heightDp = 760, showBackground = true)
@Preview(name = "Activation unknown 200 percent", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Composable private fun UnknownSample() = DesignSample(HomePanel.Status, example().copy(isManager = false, ksuVersion = null, isRootAvailable = false))

@Preview(name = "Activation checking", widthDp = 393, heightDp = 760, showBackground = true)
@Composable private fun CheckingSample() = DesignSample(HomePanel.Status, example().copy(loaded = false))

@Preview(name = "Versions HTTP 403", widthDp = 393, heightDp = 760, showBackground = true)
@Composable private fun VersionsSample() = DesignSample(HomePanel.Versions, example().copy(huskyUpdateStatus = HuskyUpdateStatus.Error, huskyError = "HTTP 403: update lookup was rejected"))

@Preview(name = "Device information", widthDp = 393, heightDp = 760, showBackground = true)
@Composable private fun DeviceSample() = DesignSample(HomePanel.Device, example())
