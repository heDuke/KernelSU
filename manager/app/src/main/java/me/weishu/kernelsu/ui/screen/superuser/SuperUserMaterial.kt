package me.weishu.kernelsu.ui.screen.superuser

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.data.model.SuperUserException
import me.weishu.kernelsu.data.model.SuperUserFailure
import me.weishu.kernelsu.data.model.SuperUserFilter
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.viewmodel.AppSortType
import kotlinx.coroutines.launch

@Composable
fun SuperUserPagerMaterial(uiState: SuperUserUiState, actions: SuperUserActions, bottomInnerPadding: Dp) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var selectedUid by rememberSaveable { mutableStateOf<Int?>(null) }
    var grantReview by remember { mutableStateOf<GroupedApps?>(null) }
    val selectedGroup = uiState.groupedApps.find { it.uid == selectedUid }
    val hasDataOrCanQuery = uiState.error == null || uiState.groupedApps.isNotEmpty()
    val enabled = uiState.hasLoaded && !uiState.isRefreshing && uiState.savingUid == null && uiState.error == null
    val setAuthorization: (GroupedApps, Boolean) -> Unit = { group, allowed ->
        if (enabled) {
            if (allowed) grantReview = group else actions.onSetAuthorization(group.authorizationTarget(), false)
        }
    }
    val errorMessage = uiState.mutationError?.let { superUserFailureMessage(it) }
    val mutationMessage = uiState.mutationMessage?.let {
        stringResource(if (it) R.string.su_grant_success else R.string.su_revoke_success)
    }
    LaunchedEffect(errorMessage, mutationMessage) {
        val message = errorMessage ?: mutationMessage
        if (message != null) {
            snackbar.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    LaunchedEffect(uiState.filter, uiState.sortConfig, uiState.showSystemApps, uiState.showOnlyPrimaryUserApps) {
        list.scrollToItem(0)
    }
    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = uiState.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                snackbarHostState = snackbar,
                scrollBehavior = scroll,
                showSearch = hasDataOrCanQuery,
                actions = {
                    IconButton(onClick = actions.onOpenSulog) {
                        Icon(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.settings_sulog))
                    }
                    SuperUserMenu(uiState, actions)
                },
                defaultContent = { bottomPadding, closeSearch ->
                    SuperUserSearchResults(uiState, uiState.recentlyInstalledResults,
                        stringResource(R.string.su_search_scope), bottomPadding,
                        onOpen = { closeSearch(); selectedUid = it.uid })
                },
                searchContent = { bottomPadding, closeSearch ->
                    SuperUserSearchResults(uiState, uiState.searchResults,
                        stringResource(R.string.su_search_scope), bottomPadding,
                        onOpen = { closeSearch(); selectedUid = it.uid })
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = list,
                modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInnerPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasDataOrCanQuery) {
                    item(key = "summary") { SuperUserOverview(uiState, actions.onFilterChange) }
                    item(key = "filters") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuperUserFilter.entries.forEach { filter ->
                                FilterChip(selected = uiState.filter == filter,
                                    onClick = { actions.onFilterChange(filter) },
                                    label = { Text(superUserFilterLabel(filter)) },
                                    leadingIcon = if (uiState.filter == filter) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null)
                            }
                        }
                        if (uiState.showSystemApps || uiState.showOnlyPrimaryUserApps) {
                            Text(listOfNotNull(
                                stringResource(R.string.show_system_apps).takeIf { uiState.showSystemApps },
                                stringResource(R.string.show_only_primary_user_apps).takeIf { uiState.showOnlyPrimaryUserApps },
                            ).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (uiState.error != null) {
                    item(key = "error") { SuperUserReadError(uiState.error, actions.onRefresh, actions.onOpenHome) }
                }
                if (!uiState.hasLoaded) {
                    item(key = "loading") {
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(R.string.su_loading))
                        }
                    }
                } else if (uiState.error == null && uiState.visibleGroups.isEmpty()) {
                    item(key = "empty") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(when (uiState.filter) {
                                    SuperUserFilter.Granted -> R.string.su_no_grants
                                    SuperUserFilter.Custom -> R.string.su_no_custom
                                    SuperUserFilter.All -> R.string.superuser_empty
                                }), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.su_empty_guidance))
                                if (uiState.filter != SuperUserFilter.All) TextButton(onClick = { actions.onFilterChange(SuperUserFilter.All) }) {
                                    Text(stringResource(R.string.su_show_all))
                                }
                            }
                        }
                    }
                }
                items(uiState.visibleGroups, key = { it.uid }) { group ->
                    SuperUserAppRow(group, uiState.savingUid == group.uid, enabled,
                        onOpen = { selectedUid = group.uid },
                        onGrantChange = { setAuthorization(group, it) })
                }
            }
        }
    }
    if (selectedGroup != null) {
        SuperUserAppSheet(selectedGroup, uiState, onDismiss = { selectedUid = null },
            onGrantChange = { setAuthorization(selectedGroup, it) },
            onOpenProfile = { selectedUid = null; actions.onOpenProfile(selectedGroup) })
    }
    grantReview?.let { group ->
        SuperUserGrantConfirmation(group, enabled = enabled,
            onDismiss = { grantReview = null },
            onConfirm = { actions.onSetAuthorization(group.authorizationTarget(), true); grantReview = null })
    }
}

@Composable
private fun SuperUserOverview(state: SuperUserUiState, onFilter: (SuperUserFilter) -> Unit) {
    val granted = state.groupedApps.filter { it.anyAllowSu }
    val colors = MaterialTheme.colorScheme
    val confirmed = state.hasLoaded && !state.isRefreshing && state.error == null
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (confirmed) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (confirmed) colors.onPrimaryContainer else colors.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.su_overview), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            if (state.hasLoaded && (!state.isRefreshing || state.groupedApps.isNotEmpty()) &&
                (state.error == null || state.groupedApps.isNotEmpty())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onFilter(SuperUserFilter.Granted) }) {
                        Text(stringResource(R.string.su_group_count, granted.size, granted.sumOf { it.apps.size }))
                    }
                    FilledTonalButton(onClick = { onFilter(SuperUserFilter.Custom) }) {
                        Text(stringResource(R.string.su_custom_count, state.groupedApps.count { it.anyCustom }))
                    }
                }
                Text(stringResource(if (state.error != null || state.isRefreshing) R.string.su_previous_snapshot else R.string.su_count_scope),
                    style = MaterialTheme.typography.bodySmall)
            } else Text(stringResource(if (state.error != null) R.string.su_state_unknown else R.string.su_loading))
        }
    }
}

@Composable
internal fun SuperUserAppRow(group: GroupedApps, saving: Boolean, enabled: Boolean,
                             onOpen: () -> Unit, onGrantChange: ((Boolean) -> Unit)? = null) {
    val description = stringResource(R.string.su_toggle_description, group.primary.label)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(modifier = Modifier.clickable(onClick = onOpen),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { AppIconImage(packageInfo = group.primary.packageInfo, label = group.primary.label, modifier = Modifier.size(48.dp)) },
            content = { Text(group.primary.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.primary.packageName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(buildList {
                        add(stringResource(if (group.anyAllowSu) R.string.app_profile_granted else R.string.app_profile_not_granted))
                        if (group.apps.size > 1) add(stringResource(R.string.su_shared_count, group.apps.size))
                        if (group.anyCustom) add(stringResource(R.string.superuser_tag_custom))
                        if (group.uid / 100000 != 0) add(stringResource(R.string.superuser_tag_user, group.uid / 100000))
                    }.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                }
            }, trailingContent = if (onGrantChange != null) ({
                if (saving) CircularProgressIndicator(Modifier.size(24.dp))
                else Switch(checked = group.anyAllowSu, onCheckedChange = onGrantChange, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = description })
            }) else null)
    }
}

@Composable
private fun SuperUserSearchResults(state: SuperUserUiState, groups: List<GroupedApps>, title: String,
                                   bottom: Dp, onOpen: (GroupedApps) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(title, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge) }
        if (state.error != null) item { Text(superUserFailureMessage(state.error), color = MaterialTheme.colorScheme.error) }
        else if (!state.hasLoaded || state.searchStatus.resultStatus == SearchStatus.ResultStatus.LOAD) {
            item { CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp)) }
        } else if (state.searchStatus.searchText.isEmpty()) {
            item { Text(stringResource(R.string.su_search_hint), style = MaterialTheme.typography.bodyMedium) }
        } else if (groups.isEmpty()) {
            item { Text(stringResource(R.string.su_no_search_results), style = MaterialTheme.typography.bodyMedium) }
        }
        items(groups, key = { it.uid }) { group ->
            SuperUserAppRow(group, false, false, onOpen = { onOpen(group) })
            // A match on any shared member keeps the complete UID group available.
            if (group.matchedPackageNames.isNotEmpty()) group.apps.filter {
                it.packageName in group.matchedPackageNames && it.packageName != group.primary.packageName
            }.forEach { Text(it.label + " · " + it.packageName, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SuperUserMenu(state: SuperUserUiState, actions: SuperUserActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.su_filters_sort)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.show_system_apps)) },
                leadingIcon = { Checkbox(state.showSystemApps, onCheckedChange = null) },
                onClick = { actions.onToggleShowSystemApps(); expanded = false })
            if (state.userIds.size > 1) DropdownMenuItem(text = { Text(stringResource(R.string.show_only_primary_user_apps)) },
                leadingIcon = { Checkbox(state.showOnlyPrimaryUserApps, onCheckedChange = null) },
                onClick = { actions.onToggleShowOnlyPrimaryUserApps(); expanded = false })
            HorizontalDivider()
            listOf(AppSortType.NAME to R.string.sort_by_name, AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                AppSortType.INSTALL_TIME to R.string.sort_by_install_time, AppSortType.UPDATE_TIME to R.string.sort_by_update_time).forEach { (type, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(state.sortConfig.sortType == type, onClick = null) },
                    onClick = { actions.onUpdateSortConfig(state.sortConfig.withType(type)); expanded = false })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.sort_reverse)) },
                leadingIcon = { Checkbox(state.sortConfig.reversed, onCheckedChange = null) },
                onClick = { actions.onUpdateSortConfig(state.sortConfig.toggleReversed()); expanded = false })
        }
    }
}

@Composable
private fun superUserFilterLabel(filter: SuperUserFilter): String = stringResource(when (filter) {
    SuperUserFilter.All -> R.string.su_filter_all
    SuperUserFilter.Granted -> R.string.su_filter_granted
    SuperUserFilter.Custom -> R.string.su_filter_custom
})

@Composable
private fun SuperUserReadError(error: Throwable, onRetry: () -> Unit, onOpenHome: () -> Unit) {
    val failure = error as? SuperUserException
    val reason = failure?.reason
    val expectedBlock = reason in setOf(SuperUserFailure.DriverUnavailable,
        SuperUserFailure.ManagerUnauthorized, SuperUserFailure.RootUnavailable)
    val colors = MaterialTheme.colorScheme
    var showDetails by remember(error) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val report = buildString {
        appendLine("app_id=${BuildConfig.APPLICATION_ID}")
        appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        append(failure?.diagnosticReport() ?: "exception=${error.javaClass.simpleName}: ${error.message.orEmpty().take(500)}")
    }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer, contentColor = colors.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = MaterialTheme.shapes.large,
                color = if (expectedBlock) colors.surfaceContainerHigh else colors.errorContainer,
                contentColor = if (expectedBlock) colors.onSurface else colors.onErrorContainer) {
                Icon(if (expectedBlock) Icons.Outlined.Info else Icons.Outlined.ErrorOutline, null,
                    Modifier.padding(12.dp).size(28.dp))
            }
            Text(stringResource(when (reason) {
                SuperUserFailure.DriverUnavailable -> R.string.su_title_driver
                SuperUserFailure.ManagerUnauthorized -> R.string.su_title_manager
                SuperUserFailure.InterfaceMismatch -> R.string.su_title_interface
                SuperUserFailure.RootUnavailable -> R.string.su_title_root
                SuperUserFailure.ServiceTimeout -> R.string.su_title_timeout
                else -> R.string.su_read_failed
            }), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(superUserFailureMessage(error))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onOpenHome) { Text(stringResource(R.string.su_check_home)) }
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.su_recheck)) }
            }
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(stringResource(if (showDetails) R.string.su_hide_diagnostics else R.string.su_diagnostics))
            }
            if (showDetails) {
                SelectionContainer { Text(report, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("HuskySU authorization diagnostics", report)))
                } }) { Text(stringResource(R.string.su_copy_diagnostics)) }
            }
        }
    }
}
