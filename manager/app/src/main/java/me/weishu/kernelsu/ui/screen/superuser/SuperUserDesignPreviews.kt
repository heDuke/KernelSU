package me.weishu.kernelsu.ui.screen.superuser

import android.content.pm.PackageInfo
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AppInfo

/** Design-only fixtures; package icons deliberately use the native placeholder. */
private fun exampleGroup(shared: Boolean = false, granted: Boolean = true): GroupedApps {
    val uid = if (shared) 110123 else 10123
    val members = (1..if (shared) 5 else 1).map { index ->
        val pkg = "example.long.packagename.authorization.app$index"
        val info = PackageInfo().apply {
            packageName = pkg
            versionName = "1.2.3"
            setLongVersionCode(123)
        }
        AppInfo(if (shared) "文件与备份工具 $index" else "示例文件管理器及长应用名称", info,
            Natives.Profile(pkg, uid, allowSu = granted, rootUseDefault = !shared))
    }
    return GroupedApps(uid, members, members.first(), granted, shared, !granted)
}

@Composable
private fun DesignTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Preview(name = "Superuser overview", widthDp = 393, heightDp = 300, showBackground = true)
@Preview(name = "Superuser overview dark", widthDp = 393, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverviewExample() = DesignTheme {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        SuperUserOverview(SuperUserUiState(hasLoaded = true, groupedApps = listOf(exampleGroup(), exampleGroup(true)))) {}
    }
}

@Preview(name = "Application rows", widthDp = 393, heightDp = 650, showBackground = true)
@Preview(name = "Application rows 200 percent narrow", widthDp = 320, heightDp = 900, fontScale = 2f)
@Composable
private fun RowsExample() = DesignTheme {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SuperUserAppRow(exampleGroup(), false, true, {}, {})
        SuperUserAppRow(exampleGroup(true, false), false, true, {}, {})
        SuperUserAppRow(exampleGroup(), false, false, {}, {}, confirmed = false)
    }
}

@Preview(name = "Shared authorization sheet", widthDp = 393, heightDp = 900)
@Preview(name = "Shared sheet dark 200 percent", widthDp = 360, heightDp = 900, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SharedSheetExample() = DesignTheme {
    val group = exampleGroup(true)
    SuperUserAppSheet(group, SuperUserUiState(hasLoaded = true, groupedApps = listOf(group)), {}, {}, {},
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded))
}
