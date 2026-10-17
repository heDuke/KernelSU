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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
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
                        onGrantChange = { setAuthorization(group, it) },
                        confirmed = uiState.error == null && !uiState.isRefreshing)
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
internal fun SuperUserOverview(state: SuperUserUiState, onFilter: (SuperUserFilter) -> Unit) {
    val granted = state.groupedApps.filter { it.anyAllowSu }
    val custom = state.groupedApps.filter { it.anyCustom }
    val colors = MaterialTheme.colorScheme
    val confirmed = state.hasLoaded && !state.isRefreshing && state.error == null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.su_overview), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (state.hasLoaded && (!state.isRefreshing || state.groupedApps.isNotEmpty()) &&
            (state.error == null || state.groupedApps.isNotEmpty())) {
            val largeText = LocalDensity.current.fontScale >= 1.5f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val metrics: @Composable RowScope.() -> Unit = {
                    SuperUserMetric(granted.size, granted.sumOf { it.apps.size },
                        stringResource(R.string.su_metric_granted), confirmed, primary = true,
                        onClick = { onFilter(SuperUserFilter.Granted) }, modifier = Modifier.weight(1f))
                    SuperUserMetric(custom.size, custom.sumOf { it.apps.size },
                        stringResource(R.string.su_metric_custom), confirmed, primary = false,
                        onClick = { onFilter(SuperUserFilter.Custom) }, modifier = Modifier.weight(1f))
                }
                if (largeText || maxWidth < 340.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuperUserMetric(granted.size, granted.sumOf { it.apps.size },
                            stringResource(R.string.su_metric_granted), confirmed, primary = true,
                            onClick = { onFilter(SuperUserFilter.Granted) })
                        SuperUserMetric(custom.size, custom.sumOf { it.apps.size },
                            stringResource(R.string.su_metric_custom), confirmed, primary = false,
                            onClick = { onFilter(SuperUserFilter.Custom) })
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = metrics)
            }
            Text(stringResource(if (confirmed) R.string.su_scope_compact else R.string.su_previous_snapshot),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (state.isRefreshing || !state.hasLoaded) CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(if (state.error != null) R.string.su_state_unknown else R.string.su_loading))
                }
            }
        }
    }
}

@Composable
private fun SuperUserMetric(groups: Int, applications: Int, label: String, confirmed: Boolean,
                            primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        !confirmed -> colors.surfaceContainerHigh
        primary -> colors.primaryContainer
        else -> colors.secondaryContainer
    }
    val foreground = when {
        !confirmed -> colors.onSurface
        primary -> colors.onPrimaryContainer
        else -> colors.onSecondaryContainer
    }
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(groups.toString(), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.su_metric_apps, applications), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SuperUserAppRow(group: GroupedApps, saving: Boolean, enabled: Boolean,
                             onOpen: () -> Unit, onGrantChange: ((Boolean) -> Unit)? = null,
                             confirmed: Boolean = true) {
    val description = if (group.apps.size > 1) stringResource(R.string.su_toggle_shared_description, group.uid, group.apps.size)
        else stringResource(R.string.su_toggle_description, group.primary.label)
    val grantLabel = stringResource(if (group.anyAllowSu) R.string.app_profile_granted else R.string.app_profile_not_granted)
    val status = when {
        saving -> stringResource(R.string.su_saving_authorization)
        !confirmed -> stringResource(R.string.su_previous_authorization, grantLabel)
        else -> grantLabel
    }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val openLabel = stringResource(R.string.su_open_details)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val separateControl = onGrantChange != null && (largeText || maxWidth < 340.dp)
            val control: @Composable () -> Unit = {
                if (saving) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = status })
                else if (onGrantChange != null) Switch(checked = group.anyAllowSu, onCheckedChange = onGrantChange,
                    enabled = enabled, modifier = Modifier.semantics {
                        contentDescription = description
                        stateDescription = status
                    })
            }
            Column {
                ListItem(modifier = Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { AppIconImage(packageInfo = group.primary.packageInfo, label = group.primary.label, modifier = Modifier.size(44.dp)) },
                    overlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (group.anyAllowSu) Icons.Outlined.Shield else Icons.Outlined.Block, null, Modifier.size(16.dp))
                            Text(status, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    content = {
                        Text(if (group.apps.size > 1) stringResource(R.string.su_shared_headline, group.primary.label, group.apps.size)
                            else group.primary.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(group.primary.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val metadata = buildList {
                                if (group.apps.size > 1) add(stringResource(R.string.su_shared_uid_short, group.uid))
                                if (group.anyCustom) add(stringResource(R.string.superuser_tag_custom))
                                if (group.uid / 100000 != 0) add(stringResource(R.string.superuser_tag_user, group.uid / 100000))
                            }
                            if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    trailingContent = when {
                        separateControl -> null
                        onGrantChange != null -> control
                        else -> ({ Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp)) })
                    })
                if (separateControl) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.su_access_control), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        control()
                    }
                }
            }
        }
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
            SuperUserAppRow(group, false, false, onOpen = { onOpen(group) },
                confirmed = state.error == null && !state.isRefreshing)
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
