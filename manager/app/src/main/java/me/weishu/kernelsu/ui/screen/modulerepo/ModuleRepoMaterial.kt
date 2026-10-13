package me.weishu.kernelsu.ui.screen.modulerepo

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.markdown.GithubMarkdown
import me.weishu.kernelsu.ui.component.module.ModuleArtwork
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import me.weishu.kernelsu.ui.component.module.ModuleInstallTarget
import me.weishu.kernelsu.ui.component.module.sizeLabel
import me.weishu.kernelsu.ui.util.download

@Composable
fun ModuleRepoScreenMaterial(state: ModuleRepoUiState, actions: ModuleRepoActions, showBack: Boolean = true, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    var manageSources by remember { mutableStateOf(false) }
    var selectedSource by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    if (manageSources) {
        ModalBottomSheet(onDismissRequest = { manageSources = false }) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item { Text(stringResource(R.string.module_sheet_sources), style = MaterialTheme.typography.headlineSmall) }
                items(state.sources, key = { it.source.id }) { source ->
                    ListItem(headlineContent = { Text(source.source.name) },
                        supportingContent = { Text(buildString {
                            append(source.source.indexUrl)
                            append("\n${if (source.lastSync > 0) source.count.toString() else "—"} · ")
                            append(stringResource(when {
                                !source.enabled -> R.string.module_sheet_source_disabled
                                source.cached -> R.string.module_sheet_cached
                                source.lastSync > 0 -> R.string.module_sheet_synced
                                else -> R.string.module_sheet_not_synced
                            }))
                            if (source.lastSync > 0) append("\n" + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(source.lastSync)))
                            source.error?.let { append("\n$it") }
                        }) },
                        trailingContent = { Switch(source.enabled, enabled = !state.isRefreshing,
                            onCheckedChange = { actions.onSetSourceEnabled(source.source.id, it) }) })
                }
                item { TextButton(onClick = { manageSources = false }) { Text(stringResource(R.string.close)) } }
            }
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.module_repos)) },
                navigationIcon = { if (showBack) IconButton(shapes = IconButtonDefaults.shapes(), onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.module_sheet_back))
                } }, actions = { IconButton(shapes = IconButtonDefaults.shapes(), onClick = actions.onRefresh, enabled = !state.isRefreshing) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.network_retry))
                } }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + bottomPadding)) {
                item(key = "catalog_controls") {
                    Column {
            TextButton(onClick = { manageSources = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.module_sheet_sources))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedSource == null, onClick = { selectedSource = null; selectedCategory = null },
                    label = { Text(stringResource(R.string.module_sheet_all_sources)) }) }
                items(state.sources.filter { it.enabled }, key = { it.source.id }) { source ->
                    FilterChip(selected = selectedSource == source.source.id,
                        onClick = { selectedSource = source.source.id; selectedCategory = null }, label = { Text(source.source.name) })
                }
            }
            val categories = state.modules.filter { selectedSource == null || it.sourceId == selectedSource }
                .flatMap { it.categories }.distinct().sorted()
            if (categories.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.module_sheet_all_categories)) }) }
                items(categories, key = { it }) { category ->
                    FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
                }
            }
            OutlinedTextField(value = state.searchStatus.searchText, onValueChange = actions.onSearchTextChange,
                label = { Text(stringResource(R.string.module_sheet_search)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(RepoSort.UPDATED to R.string.module_repos_sort_updated,
                    RepoSort.CREATED to R.string.module_repos_sort_created,
                    RepoSort.NAME to R.string.module_repos_sort_name,
                    RepoSort.STARS to R.string.module_repos_sort_stars), key = { it.first }) { (order, label) ->
                    FilterChip(selected = state.sortOrder == order, onClick = { actions.onSetSortOrder(order) },
                        label = { Text(stringResource(label)) })
                }
            }
                    }
                }
                    state.sources.filter { it.enabled && it.error != null }.forEach { source -> item {
                        ListItem(headlineContent = { Text(source.source.name) },
                            supportingContent = { Text((if (source.cached) stringResource(R.string.module_sheet_cached) + "\n" else "") + source.error) })
                    } }
                    state.error?.let { error -> item {
                        ListItem(headlineContent = { Text(error.message ?: stringResource(R.string.network_offline)) },
                            supportingContent = { Text(stringResource(R.string.module_sheet_compatibility_unknown)) })
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = actions.onRefresh, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.network_retry))
                        }
                    } }
                    if (state.isRefreshing && state.modules.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp)) { LoadingIndicator() }
                    }
                    val modules = (if (state.searchStatus.searchText.isBlank()) state.modules else state.searchResults)
                        .filter { selectedSource == null || it.sourceId == selectedSource }
                        .filter { selectedCategory == null || selectedCategory in it.categories }
                    if (modules.isEmpty() && !state.isRefreshing && state.error == null) item {
                        ListItem(headlineContent = { Text(stringResource(R.string.module_repos_empty)) })
                    }
                    items(modules, key = { "${it.sourceId}:${it.moduleId}" }) { module ->
                        ListItem(
                            modifier = Modifier.clickable { actions.onOpenRepoDetail(module) },
                            leadingContent = { ModuleArtwork(module.iconUrl, null, Modifier.size(48.dp)) },
                            headlineContent = { Text(module.moduleName) },
                            supportingContent = {
                                Text(listOf(module.authors, module.latestRelease, module.sourceName, module.summary)
                                    .filter(String::isNotBlank).joinToString("\n"))
                            },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
    }
}

@Composable
fun ModuleRepoDetailScreenMaterial(
    state: ModuleRepoDetailUiState,
    actions: ModuleRepoDetailActions,
    readmeError: String? = null,
    onRetryReadme: () -> Unit = {},
) {
    val module = state.module
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by androidx.compose.runtime.saveable.rememberSaveable(module.moduleId, module.sourceId) { mutableIntStateOf(0) }
    var downloadMessage by remember(module) { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    fun review(asset: ReleaseAssetArg, version: String) {
        ModuleInstallController.review(listOf(ModuleInstallTarget(asset.name, downloadUrl = asset.downloadUrl,
            version = version, source = module.sourceName.ifBlank { module.sourceUrl }, moduleId = module.moduleId,
            size = asset.size, minApi = module.minApi, maxApi = module.maxApi,
            kernelSuUnsupported = module.kernelSuUnsupported, dependencies = module.dependencies)), module)
    }
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        LargeFlexibleTopAppBar(title = { Text(module.moduleName) },
            navigationIcon = { IconButton(shapes = IconButtonDefaults.shapes(), onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.module_sheet_back))
            } }, actions = { IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT,
                        listOf(module.moduleName, module.latestRelease, module.sourceUrl).filter(String::isNotBlank).joinToString("\n"))
                }, null))
            }) { Icon(Icons.Outlined.Share, stringResource(R.string.module_sheet_share)) } },
            scrollBehavior = scrollBehavior)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModuleArtwork(module.iconUrl, null, Modifier.size(64.dp))
                    Column {
                        Text(module.moduleName, style = MaterialTheme.typography.headlineMedium)
                        Text(module.authors, style = MaterialTheme.typography.bodyLarge)
                        Text(module.latestRelease, style = MaterialTheme.typography.titleMedium)
                        if (module.installedVersion.isNotBlank()) Text(stringResource(R.string.module_sheet_installed_version, module.installedVersion))
                    }
                }
                val asset = state.detailReleases.firstOrNull()?.assets?.firstOrNull()
                if (asset != null) {
                    Button(shapes = ButtonDefaults.shapes(), enabled = !module.kernelSuUnsupported, onClick = { review(asset, state.detailReleases.firstOrNull()?.name.orEmpty()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.install))
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        scope.launch {
                            downloadMessage = context.getString(R.string.module_sheet_downloading)
                            val uri = download(asset.downloadUrl, asset.name)
                            downloadMessage = if (uri != null) context.getString(R.string.module_sheet_downloaded)
                                else context.getString(R.string.module_pending_download_failed, asset.name)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.module_sheet_download_only)) }
                }
                downloadMessage?.let { Text(it) }
            }
            item {
                Text(module.summary.ifBlank { stringResource(R.string.module_sheet_no_introduction) })
                ListItem(headlineContent = { Text(stringResource(R.string.module_sheet_source)) },
                    supportingContent = { Text(module.sourceName.ifBlank { module.sourceUrl }) })
                ListItem(headlineContent = { Text("ID") }, supportingContent = { Text(module.moduleId) })
                Text(stringResource(if (module.kernelSuUnsupported) R.string.module_sheet_kernel_su_unsupported else R.string.module_sheet_compatibility_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf(R.string.tab_readme, R.string.tab_releases, R.string.tab_info).forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                    }
                }
            }
            when (tab) {
                0 -> item {
                    when {
                        !state.readmeLoaded -> LoadingIndicator()
                        readmeError != null -> {
                            Text(readmeError, color = MaterialTheme.colorScheme.error)
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetryReadme) { Text(stringResource(R.string.network_retry)) }
                        }
                        !state.readmeHtml.isNullOrBlank() -> GithubMarkdown(state.readmeHtml, isMarkdown = true)
                        else -> Text(stringResource(R.string.module_sheet_no_introduction))
                    }
                }
                1 -> {
                    if (state.detailReleases.isEmpty()) item { Text(stringResource(R.string.module_repos_no_releases)) }
                    items(state.detailReleases, key = { it.tagName }) { release ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(release.name, style = MaterialTheme.typography.titleLarge)
                                Text(release.publishedAt)
                                if (release.descriptionHTML.startsWith("https://") || release.descriptionHTML.startsWith("http://")) {
                                    TextButton(onClick = { actions.onOpenUrl(release.descriptionHTML) }) { Text(stringResource(R.string.module_sheet_changelog)) }
                                } else if (release.descriptionHTML.isNotBlank()) Text(release.descriptionHTML)
                                release.assets.forEach { asset ->
                                    Text(asset.name, style = MaterialTheme.typography.titleMedium)
                                    Text(sizeLabel(asset.size))
                                    Button(shapes = ButtonDefaults.shapes(), onClick = { review(asset, release.name) }) { Text(stringResource(R.string.install)) }
                                }
                            }
                        }
                    }
                }
                2 -> item {
                    ListItem(headlineContent = { Text(stringResource(R.string.module_sheet_repository_flag)) },
                        supportingContent = { Text(stringResource(when(module.repositoryVerified) {
                            true -> R.string.module_sheet_repository_flag_yes
                            false -> R.string.module_sheet_repository_flag_no
                            null -> R.string.module_sheet_repository_flag_unknown
                        })) })
                    if (module.metamodule) Text("META")
                    if (module.license.isNotBlank()) ListItem(headlineContent = { Text(stringResource(R.string.module_sheet_license)) }, supportingContent = { Text(module.license) })
                    if (module.notes.isNotBlank()) Text(module.notes)
                    if (module.categories.isNotEmpty()) Text(module.categories.joinToString(" · "))
                    if (module.managerRequirements.isNotBlank()) ListItem(
                        headlineContent = { Text(stringResource(R.string.module_sheet_manager_requirements)) },
                        supportingContent = { Text(module.managerRequirements) })
                    if (module.permissions.isNotEmpty()) ListItem(headlineContent = { Text(stringResource(R.string.module_sheet_permissions)) },
                        supportingContent = { Text(module.permissions.joinToString("\n")) })
                    if (module.dependencies.isNotEmpty()) ListItem(headlineContent = { Text(stringResource(R.string.module_sheet_dependencies)) },
                        supportingContent = { Text(module.dependencies.joinToString("\n")) })
                    if (module.supportUrl.isNotBlank()) TextButton(onClick = { actions.onOpenUrl(module.supportUrl) }) { Text(stringResource(R.string.module_sheet_support)) }
                    if (module.donateUrl.isNotBlank()) TextButton(onClick = { actions.onOpenUrl(module.donateUrl) }) { Text(stringResource(R.string.module_sheet_donate)) }
                    Text(module.sourceUrl)
                    if (module.sourceUrl.isNotBlank()) TextButton(shapes = ButtonDefaults.shapes(), onClick = actions.onOpenWebUrl) {
                        Text(stringResource(R.string.module_sheet_source))
                    }
                    Text(module.latestReleaseTime)
                    module.authorsList.filter { it.link.isNotBlank() }.forEach { author ->
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { actions.onOpenUrl(author.link) }) { Text(author.name) }
                    }
                }
            }
        }
    }
}
