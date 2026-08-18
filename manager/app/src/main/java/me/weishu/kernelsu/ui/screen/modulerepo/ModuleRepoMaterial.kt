package me.weishu.kernelsu.ui.screen.modulerepo

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.RepoModule
import me.weishu.kernelsu.ui.component.PagerNavigationSpringSpec
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.dialog.ConfirmDialogHandle
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.markdown.GithubMarkdown
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveNoticeCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSectionTitle
import me.weishu.kernelsu.ui.component.material.ExpressiveTabRow
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItemContainer
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.rememberContentReady

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleRepoScreenMaterial(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(text = stringResource(R.string.module_repos)) },
                searchText = state.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showSortMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(expanded = showSortMenu, onDismissRequest = {
                            showSortMenu = false
                        }) {
                            val sortOptions = listOf(
                                RepoSort.UPDATED to R.string.module_repos_sort_updated,
                                RepoSort.CREATED to R.string.module_repos_sort_created,
                                RepoSort.NAME to R.string.module_repos_sort_name,
                                RepoSort.STARS to R.string.module_repos_sort_stars,
                            )
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                sortOptions.forEachIndexed { index, (order, resId) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = state.sortOrder == order,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onSetSortOrder(order)
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortOptions.size
                                        ),
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                searchContent = { _, closeSearch ->
                    val latestSearchResults = rememberUpdatedState(state.searchResults)
                    ScrollToTopOnChange(
                        searchListState,
                        state.searchStatus.searchText,
                    ) { latestSearchResults.value }
                    RepoModuleList(
                        modules = state.searchResults,
                        listState = searchListState,
                        modifier = Modifier.fillMaxSize(),
                        empty = state.searchStatus.searchText.isNotEmpty(),
                        onModuleClick = {
                            closeSearch()
                            actions.onOpenRepoDetail(it)
                        }
                    )
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val isEmpty = state.modules.isEmpty()
        val hadDataOnEntry = remember { state.modules.isNotEmpty() }
        val contentReady = hadDataOnEntry || rememberContentReady()
        val showStatus = isEmpty && (state.offline || state.error != null || (contentReady && !state.isRefreshing))
        val showSpinner = isEmpty && !showStatus

        if (showSpinner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (isEmpty) {
            RepoListStatusPane(
                state = state,
                onRetry = actions.onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
        if (!isEmpty && contentReady) {
            val latestModules = rememberUpdatedState(state.modules)
            val latestRefreshing = rememberUpdatedState(state.isRefreshing)
            ScrollToTopOnChange(
                listState,
                state.sortOrder,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestModules.value }
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isRefreshing = state.isRefreshing,
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onRefresh()
                    refreshTick.intValue++
                },
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        state = pullToRefreshState,
                    )
                },
            ) {
                RepoModuleList(
                    modules = state.modules,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    onModuleClick = actions.onOpenRepoDetail
                )
            }
        }
    }
}

@Composable
private fun RepoListStatusPane(
    state: ModuleRepoUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            state.offline -> {
                ExpressiveNoticeCard(
                    message = stringResource(R.string.network_offline),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    icon = Icons.Outlined.CloudOff,
                    action = {
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.network_retry),
                            onClick = onRetry,
                            enabled = !state.isRefreshing,
                        )
                    },
                )
            }

            state.error != null -> {
                ExpressiveNoticeCard(
                    message = state.error.localizedMessage
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.network_offline),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    icon = Icons.Outlined.Warning,
                    action = {
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.network_retry),
                            onClick = onRetry,
                            enabled = !state.isRefreshing,
                        )
                    },
                )
            }

            else -> {
                ExpressiveHeroCard(
                    title = stringResource(R.string.module_repos),
                    summary = stringResource(R.string.module_repos_empty),
                    icon = Icons.Outlined.TravelExplore,
                )
            }
        }
    }
}

@Composable
private fun RepoModuleList(
    modules: List<RepoModule>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    empty: Boolean = false,
    onModuleClick: (RepoModule) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp + bottomPadding
        ),
    ) {
        if (empty && modules.isEmpty()) {
            item(key = "repo_search_empty", contentType = "empty") {
                ExpressiveHeroCard(
                    title = stringResource(R.string.module_repos),
                    summary = stringResource(R.string.module_repos_empty),
                    icon = Icons.Outlined.TravelExplore,
                )
            }
        }
        items(modules, key = { it.moduleId }, contentType = { "module" }) { module ->
            val latestReleaseTime = remember(module.latestReleaseTime) { module.latestReleaseTime }
            val moduleAuthor = stringResource(id = R.string.module_author)

            TonalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                onClick = { onModuleClick(module) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 16.dp, 20.dp, 12.dp)
                ) {
                    if (module.moduleName.isNotEmpty()) {
                        Text(
                            text = module.moduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        )
                    }
                    if (module.moduleId.isNotEmpty()) {
                        Text(
                            text = "ID: ${module.moduleId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "$moduleAuthor: ${module.authors}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (module.summary.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = module.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 4,
                        )
                    }

                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (module.metamodule) {
                            StatusTag(
                                "META",
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                backgroundColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(thickness = Dp.Hairline)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (module.stargazerCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "stars",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = module.stargazerCount.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (latestReleaseTime.isNotEmpty()) {
                            Text(
                                text = latestReleaseTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@SuppressLint("StringFormatInvalid", "DefaultLocale")
@Composable
fun ModuleRepoDetailScreenMaterial(
    state: ModuleRepoDetailUiState,
    actions: ModuleRepoDetailActions,
) {
    val module = state.module
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmTitle = stringResource(R.string.module_install)
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = module.moduleName) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    if (state.webUrl.isNotEmpty()) {
                        IconButton(onClick = actions.onOpenWebUrl) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val tabs = listOf(
            stringResource(R.string.tab_readme),
            stringResource(R.string.tab_releases),
            stringResource(R.string.tab_info)
        )
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
        val versionLabel = stringResource(R.string.module_version)
        val authorLabel = stringResource(R.string.module_author)
        val heroSummary = remember(module.latestRelease, module.authors, versionLabel, authorLabel) {
            listOfNotNull(
                module.latestRelease.takeIf { it.isNotBlank() }?.let { "$versionLabel: $it" },
                module.authors.takeIf { it.isNotBlank() }?.let { "$authorLabel: $it" },
            ).joinToString("\n").ifBlank { null }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ExpressiveHeroCard(
                title = module.moduleName.ifBlank { module.moduleId },
                summary = heroSummary,
                icon = Icons.Outlined.Extension,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            ExpressiveTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = tabs,
                onTabClick = { scope.launch {
                    pagerState.animateScrollToPage(
                        page = it,
                        animationSpec = PagerNavigationSpringSpec,
                    )
                } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                overscrollEffect = null,
            ) { page ->
                val paddedInnerPadding = PaddingValues(
                    top = 8.dp,
                    bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
                when (page) {
                    0 -> ReadmePage(
                        readmeHtml = state.readmeHtml,
                        readmeLoaded = state.readmeLoaded,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior
                    )

                    1 -> ReleasesPage(
                        detailReleases = state.detailReleases,
                        releasesLoaded = state.readmeLoaded,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        confirmTitle = confirmTitle,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        onInstallModule = actions.onInstallModule,
                        context = context,
                        setPendingDownload = { pendingDownload = it }
                    )

                    2 -> InfoPage(
                        module = module,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        uriHandler = object : UriHandler {
                            override fun openUri(uri: String) = actions.onOpenUrl(uri)
                        },
                        sourceUrl = state.sourceUrl
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadmePage(
    readmeHtml: String?,
    readmeLoaded: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val layoutDirection = LocalLayoutDirection.current
    val isReady = rememberContentReady()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            if (readmeLoaded && readmeHtml != null) {
                var loaded by remember(readmeHtml) { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "ReadmeAlpha",
                )
                Box {
                    if (isReady) {
                        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                            GithubMarkdown(
                                content = readmeHtml,
                                onLoadingChange = { loaded = !it },
                                containerColor = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !loaded,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            } else if (readmeLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ExpressiveNoticeCard(
                        message = stringResource(R.string.module_repos_no_readme),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        icon = Icons.Outlined.Info,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ReleasesPage(
    detailReleases: List<ReleaseArg>,
    releasesLoaded: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    onInstallModule: (Uri) -> Unit,
    context: Context,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        when {
            !releasesLoaded -> {
                item(key = "releases_loading", contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }

            detailReleases.isEmpty() -> {
                item(key = "releases_empty", contentType = "empty") {
                    ExpressiveNoticeCard(
                        message = stringResource(R.string.module_repos_no_releases),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        icon = Icons.Outlined.Info,
                    )
                }
            }

            else -> {
                items(
                    items = detailReleases,
                    key = { it.tagName },
                    contentType = { "release" }
                ) { rel ->
                val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    content = buildList<@Composable () -> Unit> {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(text = title) },
                                supportingContent = rel.tagName
                                    .takeIf { it.isNotBlank() && it != title }
                                    ?.let { tag -> { Text(text = tag) } },
                                trailingContent = rel.publishedAt
                                    .takeIf { it.isNotBlank() }
                                    ?.let { date ->
                                        { Text(text = date, style = MaterialTheme.typography.bodyMedium) }
                                    },
                            )
                        }
                        if (rel.descriptionHTML.isNotEmpty()) {
                            add {
                                SegmentedItemContainer {
                                    val descReady = rememberContentReady()
                                    var descLoaded by remember(rel.descriptionHTML) { mutableStateOf(false) }
                                    val descAlpha by animateFloatAsState(
                                        targetValue = if (descLoaded) 1f else 0f,
                                        animationSpec = tween(durationMillis = 300),
                                        label = "ReleaseDescAlpha",
                                    )
                                    val descPlaceholderAlpha by animateFloatAsState(
                                        targetValue = if (descLoaded) 0f else 1f,
                                        animationSpec = tween(durationMillis = 150),
                                        label = "ReleaseDescPlaceholderAlpha",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .animateContentSize(animationSpec = tween(durationMillis = 300)),
                                    ) {
                                        if (descReady) {
                                            Box(modifier = Modifier.graphicsLayer { this.alpha = descAlpha }) {
                                                GithubMarkdown(
                                                    content = rel.descriptionHTML,
                                                    onLoadingChange = { descLoaded = !it },
                                                )
                                            }
                                        }
                                        if (descPlaceholderAlpha > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(72.dp)
                                                    .graphicsLayer { this.alpha = descPlaceholderAlpha },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                LoadingIndicator()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        rel.assets.forEach { asset ->
                            add {
                                ReleaseAssetSegmentedItem(
                                    asset = asset,
                                    confirmTitle = confirmTitle,
                                    confirmDialog = confirmDialog,
                                    scope = scope,
                                    context = context,
                                    onInstallModule = onInstallModule,
                                    setPendingDownload = setPendingDownload,
                                )
                            }
                        }
                    }
                )
            }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun ReleaseAssetSegmentedItem(
    asset: ReleaseAssetArg,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val fileName = asset.name
    val sizeText = remember(asset.size) {
        val s = asset.size
        when {
            s >= 1024L * 1024L * 1024L -> String.format("%.1f GB", s / (1024f * 1024f * 1024f))
            s >= 1024L * 1024L -> String.format("%.1f MB", s / (1024f * 1024f))
            s >= 1024L -> String.format("%.0f KB", s / 1024f)
            else -> "$s B"
        }
    }
    val sizeAndDownloads =
        remember(sizeText, asset.downloadCount) { "$sizeText · ${asset.downloadCount} downloads" }
    var isDownloading by remember(fileName, asset.downloadUrl) { mutableStateOf(false) }
    var progress by remember(fileName, asset.downloadUrl) { mutableIntStateOf(0) }
    var downloadedUri by remember(fileName, asset.downloadUrl) { mutableStateOf<Uri?>(null) }
    val isDownloaded = downloadedUri != null
    val onClickDownload = remember(fileName, asset.downloadUrl) {
        {
            val startText = context.getString(R.string.module_start_downloading, fileName)
            setPendingDownload {
                isDownloading = true
                scope.launch(Dispatchers.IO) {
                    download(
                        asset.downloadUrl,
                        fileName,
                        onDownloaded = { uri ->
                            isDownloading = false
                            downloadedUri = uri
                        },
                        onDownloading = { isDownloading = true },
                        onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } }
                    )
                }
            }
            confirmDialog.showConfirm(title = confirmTitle, content = startText)
        }
    }

    SegmentedItemContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sizeAndDownloads,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDownloading) {
                FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    CircularWavyProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (isDownloaded) {
                ExpressivePrimaryBar(
                    label = stringResource(R.string.install),
                    onClick = {
                        val uri = downloadedUri ?: return@ExpressivePrimaryBar
                        val file = uri.path?.let { java.io.File(it) }
                        if (file != null && file.exists()) {
                            onInstallModule(uri)
                        } else {
                            downloadedUri = null
                        }
                    },
                    icon = Icons.Outlined.InstallMobile,
                )
            } else {
                ExpressivePrimaryBar(
                    label = stringResource(R.string.download),
                    onClick = onClickDownload,
                    tonal = true,
                    icon = Icons.Outlined.Download,
                )
            }
        }
    }
}

@Composable
fun InfoPage(
    module: RepoModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    uriHandler: UriHandler,
    sourceUrl: String,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (module.authorsList.isNotEmpty()) {
            item(key = "authors") {
                Column {
                    ExpressiveSectionTitle(title = stringResource(R.string.module_author))
                    TonalCard(
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            module.authorsList.forEach { author ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = author.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { uriHandler.openUri(author.link) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = stringResource(R.string.open),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (sourceUrl.isNotEmpty()) {
            item(key = "source") {
                Column {
                    ExpressiveSectionTitle(title = stringResource(R.string.module_repos_source_code))
                    TonalCard(
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = sourceUrl,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ExpressivePrimaryBar(
                                label = stringResource(R.string.open),
                                onClick = { uriHandler.openUri(sourceUrl) },
                                tonal = true,
                                icon = Icons.Outlined.Link,
                            )
                        }
                    }
                }
            }
        }
    }
}
