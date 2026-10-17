package me.weishu.kernelsu.ui.screen.modulerepo

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.ModuleRepoViewModel
import okhttp3.Request

@Composable
fun ModuleRepoScreen(embedded: Boolean = false, bottomInnerPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f), isCurrentPage: Boolean = true) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ModuleRepoViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sources by viewModel.sourceStates.collectAsStateWithLifecycle()
    val installed = viewModel<me.weishu.kernelsu.ui.viewmodel.ModuleViewModel>(key = "catalog-installed")
    val installedState by installed.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(isCurrentPage) { if (isCurrentPage && me.weishu.kernelsu.Natives.isManager) installed.fetchModuleList() }
    LaunchedEffect(isCurrentPage) { if (isCurrentPage && uiState.modules.isEmpty()) viewModel.refresh() }
    ModuleRepoScreenMaterial(uiState.copy(sources = sources), ModuleRepoActions(
        onBack = { navigator.pop() }, onRefresh = viewModel::refresh,
        onSearchTextChange = viewModel::updateSearchText,
        onClearSearch = { viewModel.updateSearchText("") },
        onSearchStatusChange = viewModel::updateSearchStatus,
        onSetSortOrder = viewModel::setSortOrder,
        onSetSourceEnabled = viewModel::setSourceEnabled,
        onOpenRepoDetail = { module -> scope.launch {
            val resolved = if (module.sourceId == "kernelsu") {
                me.weishu.kernelsu.data.repository.OfficialRegistryRepository().fetchDetail(module.moduleId).map { detail ->
                    module.copy(sourceUrl = detail.sourceUrl, license = detail.license.orEmpty(),
                        supportUrl = detail.support.orEmpty(), readmeUrl = "https://modules.kernelsu.org/api/v1/modules/${module.moduleId}.json",
                        releaseHistory = detail.releases, latestAsset = detail.releases.firstOrNull()?.asset).toModuleArg()
                }
            } else Result.success(module.toModuleArg())
            resolved.onSuccess { arg -> ModuleInstallController.preview(arg.copy(
                installedVersion = installedState.modules.firstOrNull { it.id == module.moduleId && !it.remove }?.version.orEmpty(),
                installedVersionCode = installedState.modules.firstOrNull { it.id == module.moduleId && !it.remove }?.versionCode ?: 0))
            }.onFailure { android.widget.Toast.makeText(ksuApp, it.message, android.widget.Toast.LENGTH_LONG).show() }
        } },
    ), showBack = !embedded, bottomPadding = bottomInnerPadding)
}

@Composable
fun ModuleRepoDetailScreen(module: RepoModuleArg) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    var readme by remember(module) { mutableStateOf<String?>(null) }
    var loaded by remember(module) { mutableStateOf(false) }
    var error by remember(module) { mutableStateOf<String?>(null) }
    var attempt by remember(module) { mutableIntStateOf(0) }
    LaunchedEffect(module.readmeUrl, attempt) {
        loaded = false; error = null
        if (module.sourceId == "kernelsu") {
            val result = me.weishu.kernelsu.data.repository.OfficialRegistryRepository().fetchDetail(module.moduleId)
            readme = result.getOrNull()?.readmeHtml
            error = result.exceptionOrNull()?.message
        } else if (module.readmeUrl.isNotBlank()) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ksuApp.okhttpClient.newCall(Request.Builder().url(module.readmeUrl).build()).execute().use {
                        check(it.isSuccessful) { "README HTTP ${it.code}" }
                        val bytes = it.body.byteStream().use { stream -> stream.readNBytes(512 * 1024 + 1) }
                        check(bytes.size <= 512 * 1024) { "README exceeds display limit" }
                        bytes.toString(Charsets.UTF_8)
                    }
                }
            }
            readme = result.getOrNull(); error = result.exceptionOrNull()?.message
        }
        loaded = true
    }
    ModuleRepoDetailScreenMaterial(
        ModuleRepoDetailUiState(module, readme, loaded, module.releases, module.sourceUrl, module.sourceUrl),
        ModuleRepoDetailActions(onBack = { navigator.pop() },
            onOpenWebUrl = { if (module.sourceUrl.isNotBlank()) uriHandler.openUri(module.sourceUrl) },
            onOpenUrl = uriHandler::openUri,
            onInstallModule = { navigator.push(Route.Flash(FlashIt.FlashModules(listOf(it)))) }),
        readmeError = error, onRetryReadme = { attempt++ },
    )
}
