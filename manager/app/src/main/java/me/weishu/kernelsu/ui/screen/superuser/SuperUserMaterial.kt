package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.viewmodel.AppSortType

@Composable
fun SuperUserPagerMaterial(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var localSearchText by remember { mutableStateOf(uiState.searchStatus.searchText) }
    LaunchedEffect(uiState.searchStatus.searchText) {
        localSearchText = uiState.searchStatus.searchText
    }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.superuser)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onClearSearch()
                },
                navigationIcon = {
                    IconButton(onClick = actions.onOpenSulog) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
                            contentDescription = stringResource(R.string.settings_sulog)
                        )
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortEntries = listOf(
                                AppSortType.NAME to R.string.sort_by_name,
                                AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                                AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                                AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
                            )
                            val sortConfig = uiState.sortConfig

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                                sortEntries.onEachIndexed { index, (type, resId) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = sortConfig.sortType == type,
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onUpdateSortConfig(sortConfig.withType(type))
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortEntries.size
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_reverse)) },
                                    checked = sortConfig.reversed,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onUpdateSortConfig(sortConfig.toggleReversed())
                                        showSortMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(
                                        index = 0,
                                        count = 1
                                    ),
                                )
                            }
                        }
                    }

                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenuPopup(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            val filterCount = if (uiState.userIds.size > 1) 2 else 1
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.show_system_apps)) },
                                    checked = uiState.showSystemApps,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onToggleShowSystemApps()
                                        showDropdown = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = filterCount),
                                )
                                if (uiState.userIds.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.show_only_primary_user_apps)) },
                                        checked = uiState.showOnlyPrimaryUserApps,
                                        checkedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onToggleShowOnlyPrimaryUserApps()
                                            showDropdown = false
                                        },
                                        shapes = MenuDefaults.itemShape(index = 1, count = filterCount),
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        if (uiState.recentlyInstalledResults.isNotEmpty()) {
                            item {
                                SegmentedColumn(
                                    title = stringResource(R.string.recently_installed),
                                    content = uiState.recentlyInstalledResults.map { group ->
                                        @Composable {
                                            SearchGroupItem(
                                                group = group,
                                                closeSearch = closeSearch,
                                                onOpenProfile = actions.onOpenProfile,
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 0.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        itemsIndexed(uiState.searchResults, key = { _, item -> item.uid }) { index, group ->
                            SegmentedItem(index = index, count = uiState.searchResults.size) {
                                SearchGroupItem(
                                    group = group,
                                    closeSearch = closeSearch,
                                    onOpenProfile = actions.onOpenProfile,
                                )
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }

            val latestGroupedApps = rememberUpdatedState(uiState.groupedApps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortConfig,
                uiState.showSystemApps,
                uiState.showOnlyPrimaryUserApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 16.dp + bottomInnerPadding
                ),
            ) {
                item(key = "superuser_hero") {
                    SuperUserHeroCard(uiState = uiState)
                }

                if (uiState.groupedApps.isEmpty() && !uiState.isRefreshing && uiState.hasLoaded) {
                    item(key = "superuser_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.superuser_empty),
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                itemsIndexed(uiState.groupedApps, key = { _, item -> item.uid }) { index, group ->
                    val expanded = expandedSearchUids.value.contains(group.uid)
                    val onToggleExpand = {
                        if (group.apps.size > 1) {
                            expandedSearchUids.value = if (expandedSearchUids.value.contains(group.uid)) {
                                expandedSearchUids.value - group.uid
                            } else {
                                expandedSearchUids.value + group.uid
                            }
                        }
                    }
                    SegmentedItem(index = index, count = uiState.groupedApps.size) {
                        Column {
                            GroupItem(
                                group = group,
                                selected = expanded,
                                showExpandIcon = group.apps.size > 1,
                                onToggleExpand = onToggleExpand,
                            ) {
                                actions.onOpenProfile(group)
                            }
                            NestedGroupMembers(
                                group = group,
                                visible = expanded && group.apps.size > 1,
                            ) {
                                actions.onOpenProfile(group)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupItem(
    group: GroupedApps,
    closeSearch: () -> Unit,
    onOpenProfile: (GroupedApps) -> Unit,
) {
    Column {
        GroupItem(
            group = group,
            selected = false,
            showExpandIcon = false,
            onToggleExpand = {},
        ) {
            closeSearch()
            onOpenProfile(group)
        }
        NestedGroupMembers(
            group = group,
            visible = group.apps.size > 1,
        ) {
            closeSearch()
            onOpenProfile(group)
        }
    }
}

@Composable
private fun SuperUserHeroCard(uiState: SuperUserUiState) {
    val grantedCount = uiState.groupedApps.count { it.anyAllowSu }
    val appCount = uiState.groupedApps.sumOf { it.apps.size }
    val summary = if (uiState.groupedApps.isEmpty()) {
        stringResource(R.string.superuser_summary_guidance)
    } else {
        stringResource(R.string.superuser_summary, grantedCount, appCount)
    }
    ExpressiveHeroCard(
        title = stringResource(R.string.superuser),
        summary = summary,
        icon = Icons.Outlined.Shield,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

@Composable
private fun NestedGroupMembers(
    group: GroupedApps,
    visible: Boolean,
    onOpenProfile: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            group.apps.forEachIndexed { nestedIndex, app ->
                SegmentedItem(index = nestedIndex, count = group.apps.size) {
                    SimpleAppItem(
                        app = app,
                        matched = group.matchedPackageNames.contains(app.packageName),
                        onNavigate = onOpenProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: AppInfo,
    matched: Boolean = false,
    onNavigate: () -> Unit,
) {
    val colors = if (matched) {
        ListItemDefaults.segmentedColors(
            containerColor = colorScheme.secondaryContainer,
            disabledContainerColor = colorScheme.secondaryContainer,
            supportingContentColor = colorScheme.onSurfaceVariant,
        )
    } else {
        ListItemDefaults.segmentedColors(
            containerColor = colorScheme.surfaceBright,
            disabledContainerColor = colorScheme.surfaceBright,
            supportingContentColor = colorScheme.onSurfaceVariant,
        )
    }
    SegmentedListItem(
        onClick = onNavigate,
        colors = colors,
        headlineContent = { Text(app.label, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        supportingContent = { Text(app.packageName, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        leadingContent = {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier.size(40.dp)
            )
        },
    )
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    selected: Boolean,
    showExpandIcon: Boolean,
    onToggleExpand: () -> Unit,
    onClickPrimary: () -> Unit,
) {
    val bg = colorScheme.primary
    val fg = colorScheme.onPrimary
    val umountBg = colorScheme.tertiaryContainer
    val umountFg = colorScheme.onTertiaryContainer
    val customBg = colorScheme.secondaryContainer
    val customFg = colorScheme.onSecondaryContainer
    val otherBg = colorScheme.tertiary
    val otherFg = colorScheme.onTertiary

    val userId = group.uid / 100000
    val rootLabel = stringResource(R.string.superuser_tag_root)
    val umountLabel = stringResource(R.string.superuser_tag_umount)
    val customLabel = stringResource(R.string.superuser_tag_custom)
    val userLabel = if (userId != 0) {
        stringResource(R.string.superuser_tag_user, userId)
    } else {
        ""
    }
    val tags = remember(
        group.anyAllowSu,
        group.shouldUmount,
        group.anyCustom,
        userId,
        rootLabel,
        umountLabel,
        customLabel,
        userLabel,
    ) {
        buildList {
            if (group.anyAllowSu) add(StatusMeta(rootLabel, bg, fg))
            if (group.shouldUmount) add(StatusMeta(umountLabel, umountBg, umountFg))
            if (group.anyCustom) add(StatusMeta(customLabel, customBg, customFg))
            if (userId != 0) add(StatusMeta(userLabel, otherBg, otherFg))
        }
    }
    val summaryText = if (group.apps.size > 1) {
        stringResource(R.string.group_contains_apps, group.apps.size)
    } else {
        group.primary.packageName
    }
    SegmentedListItem(
        selected = selected,
        onClick = onClickPrimary,
        onLongClick = if (group.apps.size > 1) onToggleExpand else null,
        headlineContent = {
            Text(
                text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = summaryText,
                color = colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        trailingContent = {
            if (tags.isNotEmpty() || showExpandIcon) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tags.isNotEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                StatusTag(
                                    label = tag.label,
                                    backgroundColor = tag.bg,
                                    contentColor = tag.fg
                                )
                            }
                        }
                    }
                    if (showExpandIcon) {
                        Icon(
                            imageVector = if (selected) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        leadingContent = {
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier.size(48.dp)
            )
        },
    )
}
