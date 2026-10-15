package me.weishu.kernelsu.ui.screen.home

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.*
import me.weishu.kernelsu.data.repository.HomeDashboardPreferences
import me.weishu.kernelsu.ui.screen.module.ModuleUiState
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import java.text.DateFormat
import java.util.Date

internal fun DashboardCard.titleId() = when (this) {
    DashboardCard.Status -> R.string.dashboard_status
    DashboardCard.Access -> R.string.dashboard_access
    DashboardCard.Runtime -> R.string.dashboard_runtime
    DashboardCard.Incidents -> R.string.dashboard_incidents
    DashboardCard.Todos -> R.string.dashboard_todos
    DashboardCard.Shortcuts -> R.string.dashboard_shortcuts
    DashboardCard.Versions -> R.string.dashboard_versions
    DashboardCard.Modules -> R.string.dashboard_modules
    DashboardCard.Device -> R.string.dashboard_device
    DashboardCard.Compatibility -> R.string.dashboard_compatibility
}
internal fun DashboardSize.titleId() = when (this) {
    DashboardSize.Compact -> R.string.dashboard_compact
    DashboardSize.Standard -> R.string.dashboard_standard
    DashboardSize.Expanded -> R.string.dashboard_expanded
}
internal fun DashboardShortcut.titleId() = when (this) {
    DashboardShortcut.InstallModule -> R.string.dashboard_install_module
    DashboardShortcut.Flash -> R.string.dashboard_flash
    DashboardShortcut.Diagnostics -> R.string.dashboard_diagnostics
    DashboardShortcut.Reboot -> R.string.reboot
    DashboardShortcut.CheckUpdates -> R.string.dashboard_check_updates
    DashboardShortcut.Loader -> R.string.dashboard_loader
    DashboardShortcut.DisableModules -> R.string.dashboard_disable_modules
    DashboardShortcut.RestoreModules -> R.string.dashboard_restore_modules
}
private fun DashboardCard.icon(): ImageVector = when (this) {
    DashboardCard.Status -> Icons.Outlined.CheckCircle
    DashboardCard.Access -> Icons.Outlined.Security
    DashboardCard.Runtime -> Icons.Outlined.Schedule
    DashboardCard.Incidents -> Icons.Outlined.History
    DashboardCard.Todos -> Icons.Outlined.Checklist
    DashboardCard.Shortcuts -> Icons.Outlined.Bolt
    DashboardCard.Versions -> Icons.Outlined.SystemUpdate
    DashboardCard.Modules -> Icons.Outlined.Extension
    DashboardCard.Device -> Icons.Outlined.Smartphone
    DashboardCard.Compatibility -> Icons.Outlined.VerifiedUser
}
private fun incidentTitle(code: String) = when (code) {
    "safe_mode" -> R.string.dashboard_safe_mode
    "root_unavailable" -> R.string.dashboard_root_missing
    "interface_mismatch" -> R.string.dashboard_interface_mismatch
    "activation_lost" -> R.string.dashboard_activation_lost
    else -> R.string.dashboard_unknown
}
private fun duration(millis: Long): String = android.text.format.DateUtils.formatElapsedTime(millis.coerceAtLeast(0) / 1000)
private val LocalDashboardPreview = staticCompositionLocalOf { false }
private enum class HomePanel { Edit, Status, Access, Runtime, Incidents, Todos, Shortcuts, Versions, Modules, Device, Compatibility, Advanced, Backups }
private data class HomeTodo(val title: String, val detail: String = "", val open: () -> Unit)

@Composable
fun HomePagerMaterial(state: HomeUiState, actions: HomeActions, bottomInnerPadding: Dp,
                      modules: ModuleUiState = ModuleUiState(), active: Boolean = true) {
    val inspection = LocalInspectionMode.current
    val settings = if (inspection) HomeDashboardPreferences.Value(DashboardLayout(), null) else HomeDashboardPreferences.state.collectAsStateWithLifecycle().value
    val taskSummary = remember { ModuleInstallController.state.map { it.stage to it.error }.distinctUntilChanged() }
    val moduleTask = if (inspection) me.weishu.kernelsu.ui.component.module.ModuleSheetStage.PREVIEW to null else taskSummary.collectAsStateWithLifecycle(initialValue = me.weishu.kernelsu.ui.component.module.ModuleSheetStage.PREVIEW to null).value
    var panelName by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf(DashboardLayout().serialize()) }
    var base by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCard by rememberSaveable { mutableStateOf(DashboardCard.Status.name) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val panel = panelName?.let(HomePanel::valueOf)
    val editing = panel == HomePanel.Edit
    val layout = if (editing) runCatching { DashboardLayout.parse(draft) }.getOrDefault(settings.layout) else settings.layout
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    fun open(p: HomePanel) { panelName = p.name }
    fun edit() { draft = settings.layout.serialize(); base = settings.raw; error = null; open(HomePanel.Edit) }
    fun runShortcut(action: DashboardShortcut) {
        when (action) {
            DashboardShortcut.InstallModule -> { panelName = null; actions.onInstallModule() }
            DashboardShortcut.Flash -> open(HomePanel.Advanced)
            DashboardShortcut.Diagnostics -> open(HomePanel.Compatibility)
            DashboardShortcut.Reboot, DashboardShortcut.DisableModules, DashboardShortcut.RestoreModules -> confirmation = action.name
            DashboardShortcut.CheckUpdates -> actions.onCheckHuskyUpdate()
            DashboardShortcut.Loader -> { panelName = null; actions.onOpenLoader() }
        }
    }
    val unknown = stringResource(R.string.dashboard_unknown)
    val mode = when (state.lkmMode) { true -> "LKM"; false -> "GKI"; null -> unknown }
    val moduleKnown = modules.hasLoaded && modules.loadError == null && state.isFullFeatured
    val enabled = modules.modules.count { it.enabled && !it.remove }
    val disabled = modules.modules.count { !it.enabled && !it.remove }
    val pending = if (moduleKnown) modules.modules.count { it.update || it.remove } else 0
    val updates = if (moduleKnown) modules.updateInfo.count { (id, info) -> info.downloadUrl.isNotBlank() && modules.modules.any { it.id == id && !it.remove } } else 0
    val todos = buildList {
        if (state.isSafeMode) add(HomeTodo(stringResource(R.string.dashboard_safe_mode), stringResource(R.string.dashboard_safe_mode_note)) { open(HomePanel.Status) })
        if (state.showRootWarning) add(HomeTodo(stringResource(R.string.dashboard_root_missing)) { open(HomePanel.Access) })
        if (state.showVersionMismatchWarning) add(HomeTodo(stringResource(R.string.dashboard_interface_mismatch)) { open(HomePanel.Versions) })
        if (state.hasUpdate) add(HomeTodo(stringResource(R.string.dashboard_update_manager)) { open(HomePanel.Versions) })
        if (state.huskyUpdateStatus == HuskyUpdateStatus.Available) add(HomeTodo(stringResource(R.string.dashboard_update_driver), state.huskyRelease?.tag.orEmpty()) { open(HomePanel.Versions) })
        if (updates > 0) add(HomeTodo(stringResource(R.string.module_update) + " \u00b7 $updates", open = actions.onOpenModules))
        if (pending > 0) add(HomeTodo(stringResource(R.string.dashboard_pending_changes) + " \u00b7 $pending", open = actions.onOpenModules))
        state.envReport?.items?.filter { it.group == EnvCheckGroup.Modules && it.severity == EnvCheckSeverity.Fail }?.forEach {
            add(HomeTodo(it.title, it.detail, actions.onOpenModules))
        }
        modules.loadError?.let { add(HomeTodo(stringResource(R.string.dashboard_module_errors), it, actions.onOpenModules)) }
        if (moduleTask.first == me.weishu.kernelsu.ui.component.module.ModuleSheetStage.FAILED && moduleTask.second != null) add(HomeTodo(stringResource(R.string.dashboard_module_errors), moduleTask.second.orEmpty()) { panelName = null; ModuleInstallController.reopen() })
    }

    @Composable fun info(label: Int, value: String) { DashboardInfo(stringResource(label), value) }
    @Composable fun shortcuts(size: DashboardSize, all: Boolean = false) {
        val limit = if (all || size == DashboardSize.Expanded) 6 else if (size == DashboardSize.Compact) 2 else 4
        val items = layout.shortcuts.take(limit)
        items.chunked(if (LocalDensity.current.fontScale > 1.3f) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { shortcut ->
                    val privileged = shortcut in listOf(DashboardShortcut.Reboot, DashboardShortcut.DisableModules, DashboardShortcut.RestoreModules)
                    FilledTonalButton(onClick = { runShortcut(shortcut) }, shapes = ButtonDefaults.shapes(),
                        enabled = !LocalDashboardPreview.current && (!privileged || (state.isFullFeatured && !state.moduleBatchBusy)), modifier = Modifier.weight(1f)) {
                        Text(stringResource(shortcut.titleId()))
                    }
                }
                if (row.size == 1 && LocalDensity.current.fontScale <= 1.3f) Spacer(Modifier.weight(1f))
            }
        }
        if (layout.shortcuts.size > items.size) TextButton(enabled = !LocalDashboardPreview.current, onClick = { open(HomePanel.Shortcuts) }) { Text(stringResource(R.string.dashboard_more)) }
        if (!state.isFullFeatured) Text(stringResource(R.string.dashboard_permission_missing), style = MaterialTheme.typography.bodySmall)
    }
    @Composable fun content(card: DashboardCard, size: DashboardSize) {
        val compact = size == DashboardSize.Compact
        val expanded = size == DashboardSize.Expanded
        when (card) {
            DashboardCard.Status -> {
                Text(stringResource(if (!state.loaded) R.string.dashboard_loading else if (state.ksuVersion != null) R.string.dashboard_active else R.string.dashboard_inactive), style = MaterialTheme.typography.headlineSmall)
                if (state.isSafeMode) Text(stringResource(R.string.dashboard_safe_mode))
                else Text(state.systemInfo.deviceModel, style = MaterialTheme.typography.bodyMedium)
                if (!compact) Text(if (state.isSafeMode) stringResource(R.string.dashboard_safe_mode_note) else state.envReport?.items?.firstOrNull { it.group == EnvCheckGroup.Root && it.severity != EnvCheckSeverity.Pass }?.detail.orEmpty())
            }
            DashboardCard.Access -> {
                Text(stringResource(if (!state.loaded) R.string.dashboard_loading else if (state.isManager) R.string.dashboard_permission_ok else R.string.dashboard_permission_missing), style = MaterialTheme.typography.titleLarge)
                Text(mode, style = MaterialTheme.typography.headlineSmall)
                if (!compact) Text(stringResource(if (state.isRootAvailable) R.string.dashboard_root_ok else R.string.dashboard_root_missing))
                if (!compact && state.isLateLoadMode) Text(stringResource(R.string.jailbreak_reboot_warning))
                if (expanded) DashboardInfo("UAPI", "Manager ${state.managerUAPIVersion} \u00b7 Kernel ${state.kernelUAPIVersion ?: unknown}")
            }
            DashboardCard.Runtime -> {
                DashboardRuntimeValues(state, active)
                if (expanded) Text(stringResource(R.string.dashboard_observation_note))
            }
            DashboardCard.Incidents -> {
                Text(if (!state.loaded || state.runtimeError != null) unknown else state.runtime.total.toString(), style = MaterialTheme.typography.headlineMedium)
                Text(if (!state.loaded || state.runtimeError != null) unknown else state.runtime.events.lastOrNull()?.let { stringResource(incidentTitle(it.code)) } ?: stringResource(R.string.dashboard_no_incidents))
                if (expanded) state.runtime.events.takeLast(3).reversed().forEach { event ->
                    Text(DateFormat.getDateTimeInstance().format(Date(event.at)) + " \u00b7 " + stringResource(incidentTitle(event.code)))
                }
            }
            DashboardCard.Todos -> {
                if (todos.isEmpty()) Text(stringResource(R.string.dashboard_no_todos))
                else {
                    todos.take(if (compact) 1 else if (expanded) 20 else 3).forEach { todo ->
                        TextButton(enabled = !LocalDashboardPreview.current, onClick = todo.open, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                            Text(todo.title, style = MaterialTheme.typography.titleSmall)
                            if (!compact && todo.detail.isNotBlank()) Text(todo.detail, style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                    if (todos.size > if (compact) 1 else if (expanded) 20 else 3) TextButton(enabled = !LocalDashboardPreview.current, onClick = { open(HomePanel.Todos) }) { Text(stringResource(R.string.dashboard_more)) }
                }
            }
            DashboardCard.Shortcuts -> shortcuts(size)
            DashboardCard.Versions -> {
                info(R.string.dashboard_manager, state.systemInfo.managerVersion)
                info(R.string.dashboard_driver, state.ksuVersion?.let { "$it \u00b7 ${state.kernelUAPIVersion ?: unknown}" } ?: unknown)
                if (!compact) {
                    info(R.string.dashboard_mode, mode)
                    if (state.showManagerPrBuildWarning) Text(stringResource(R.string.home_pr_build_warning))
                    else if (state.showKernelPrBuildWarning) Text(stringResource(R.string.home_pr_kernel_warning))
                    if (driverVersionStatus(state.currentManagerVersionCode, state.ksuVersion, state.managerUAPIVersion, state.kernelUAPIVersion, state.requiresNewKernel) == DriverVersionStatus.BuildDifferent)
                        Text(stringResource(R.string.dashboard_build_difference), style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !LocalDashboardPreview.current, onClick = { open(HomePanel.Advanced) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.dashboard_flash)) }
                }
                if (state.lkmMode == false) Text(stringResource(R.string.dashboard_gki_source_note), style = MaterialTheme.typography.bodySmall)
                if (expanded) {
                    state.huskyRelease?.let { DashboardInfo(stringResource(R.string.dashboard_check_updates), it.tag) }
                    state.huskyError?.let { Text(it) }
                }
            }
            DashboardCard.Modules -> {
                Text(if (moduleKnown) modules.modules.size.toString() else unknown, style = MaterialTheme.typography.headlineMedium)
                if (moduleKnown) {
                    if (state.isSafeMode) Text(stringResource(R.string.dashboard_safe_mode))
                    info(if (state.isSafeMode) R.string.dashboard_configured_enabled else R.string.dashboard_enabled, enabled.toString()); info(R.string.dashboard_disabled, disabled.toString())
                    if (!compact) {
                        info(R.string.dashboard_pending, pending.toString())
                        info(R.string.module_update, updates.toString())
                        info(R.string.dashboard_metamodule, modules.modules.firstOrNull { it.metamodule && !it.remove }?.name ?: unknown)
                    }
                }
                if (expanded) Text(stringResource(R.string.dashboard_module_note))
            }
            DashboardCard.Device -> {
                Text(state.systemInfo.deviceModel, style = MaterialTheme.typography.titleLarge)
                info(R.string.dashboard_android, "${Build.VERSION.RELEASE} \u00b7 API ${Build.VERSION.SDK_INT}")
                info(R.string.dashboard_build, Build.ID)
                if (!compact) {
                    info(R.string.dashboard_kernel, state.systemInfo.kernelVersion)
                    info(R.string.dashboard_page_size, state.pageSize?.let { "$it B" } ?: unknown)
                }
                if (expanded) {
                    info(R.string.dashboard_architecture, Build.SUPPORTED_ABIS.joinToString())
                    info(R.string.dashboard_slot, state.slot?.takeIf { it.isNotBlank() } ?: unknown)
                    info(R.string.dashboard_selinux, state.systemInfo.selinuxStatus)
                    info(R.string.dashboard_seccomp, state.systemInfo.seccompStatus.takeIf { it >= 0 }?.toString() ?: unknown)
                    info(R.string.dashboard_fingerprint, state.systemInfo.fingerprint)
                }
            }
            DashboardCard.Compatibility -> {
                Text(stringResource(when (state.envOverall) {
                    EnvCheckSeverity.Pass -> R.string.dashboard_compat_pass
                    EnvCheckSeverity.Fail, EnvCheckSeverity.Warn -> R.string.dashboard_compat_conflict
                    EnvCheckSeverity.Unknown -> R.string.dashboard_compat_unknown
                    null -> R.string.dashboard_loading
                }), style = MaterialTheme.typography.titleLarge)
                Text(state.envSummaryLine ?: unknown)
                if (!compact) Text(stringResource(R.string.dashboard_validation_note), style = MaterialTheme.typography.bodySmall)
                if (expanded) {
                    Text(stringResource(R.string.dashboard_target_note))
                    state.envReport?.items?.take(3)?.forEach { DashboardInfo(it.title, it.detail) }
                }
            }
        }
    }
    @Composable fun card(card: DashboardCard, size: DashboardSize, preview: Boolean = false) {
        val alert = state.loaded && card == DashboardCard.Status && (state.isSafeMode || state.showVersionMismatchWarning || state.showRootWarning)
        val container = when {
            alert -> MaterialTheme.colorScheme.errorContainer
            card == DashboardCard.Status -> MaterialTheme.colorScheme.primaryContainer
            card == DashboardCard.Access -> MaterialTheme.colorScheme.secondaryContainer
            card == DashboardCard.Runtime -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        Card(onClick = { if (!preview) open(HomePanel.valueOf(card.name)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(card.icon(), null, Modifier.size(20.dp))
                    Text(stringResource(card.titleId()), style = MaterialTheme.typography.labelLarge)
                }
                CompositionLocalProvider(LocalDashboardPreview provides preview) { content(card, size) }
            }
        }
    }
    BoxWithConstraints {
        val columns = if (maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.35f) 2 else 1
        Scaffold(topBar = {
            LargeFlexibleTopAppBar(title = { Text("HuskySU") }, scrollBehavior = scroll, actions = {
                IconButton(onClick = actions.onRefresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.dashboard_refresh)) }
                IconButton(onClick = ::edit) { Icon(Icons.Outlined.Edit, stringResource(R.string.dashboard_edit)) }
            })
        }) { padding ->
            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.padding(padding).nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInnerPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(DashboardCard.entries, key = { it.name }, span = { GridItemSpan(if (layout.sizes.getValue(it) == DashboardSize.Compact) 1 else maxLineSpan) }) {
                    card(it, layout.sizes.getValue(it))
                }
            }
        }
    }
    if (panel != null) {
        ModalBottomSheet(sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden), onDismissRequest = { if (!saving) panelName = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(when (panel) {
                        HomePanel.Edit -> R.string.dashboard_edit
                        HomePanel.Advanced -> R.string.dashboard_advanced
                        HomePanel.Backups -> R.string.dashboard_backup
                        else -> DashboardCard.valueOf(panel.name).titleId()
                    }), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(enabled = !saving, onClick = { panelName = null }) { Icon(Icons.Outlined.Close, stringResource(android.R.string.cancel)) }
                }
                when (panel) {
                    HomePanel.Edit -> {
                        if (settings.invalid) Text(stringResource(R.string.dashboard_settings_invalid), color = MaterialTheme.colorScheme.error)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DashboardCard.entries.size) { i -> val c = DashboardCard.entries[i]
                                FilterChip(enabled = !saving, selected = selectedCard == c.name, onClick = { selectedCard = c.name }, label = { Text(stringResource(c.titleId())) })
                            }
                        }
                        val selected = DashboardCard.valueOf(selectedCard)
                        DashboardSize.entries.forEach { size ->
                            ListItem(content = { Text(stringResource(size.titleId())) }, leadingContent = {
                                RadioButton(selected = layout.sizes[selected] == size, onClick = null)
                            }, modifier = Modifier.selectable(enabled = !saving, selected = layout.sizes[selected] == size, role = Role.RadioButton,
                                onClick = { draft = layout.copy(sizes = layout.sizes + (selected to size)).serialize() }))
                        }
                        Text(stringResource(R.string.dashboard_size_note), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.dashboard_preview), style = MaterialTheme.typography.titleMedium)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth(if (layout.sizes[selected] == DashboardSize.Compact && maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.35f) .5f else 1f)) { card(selected, layout.sizes.getValue(selected), true) }
                        }
                        Text(stringResource(R.string.dashboard_shortcuts), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dashboard_shortcuts_note), style = MaterialTheme.typography.bodySmall)
                        (layout.shortcuts + DashboardShortcut.entries.filter { it !in layout.shortcuts }).forEach { shortcut ->
                            val index = layout.shortcuts.indexOf(shortcut)
                            ListItem(content = { Text(stringResource(shortcut.titleId())) }, leadingContent = {
                                Checkbox(checked = index >= 0, enabled = !saving && (index >= 0 || layout.shortcuts.size < 6) && !(index >= 0 && layout.shortcuts.size == 1), onCheckedChange = { checked ->
                                    if (checked || layout.shortcuts.size > 1) draft = layout.copy(shortcuts = if (checked) layout.shortcuts + shortcut else layout.shortcuts - shortcut).serialize()
                                })
                            }, trailingContent = {
                                if (index >= 0) Row {
                                    fun move(delta: Int) { val values = layout.shortcuts.toMutableList(); java.util.Collections.swap(values, index, index + delta); draft = layout.copy(shortcuts = values).serialize() }
                                    IconButton(enabled = !saving && index > 0, onClick = { move(-1) }) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.dashboard_move_up)) }
                                    IconButton(enabled = !saving && index < layout.shortcuts.lastIndex, onClick = { move(1) }) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.dashboard_move_down)) }
                                }
                            })
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        TextButton(enabled = !saving, onClick = { draft = DashboardLayout().serialize() }) { Text(stringResource(R.string.dashboard_reset)) }
                        Button(enabled = !saving, onClick = { saving = true; scope.launch {
                            runCatching { HomeDashboardPreferences.save(base, layout) }.fold(onSuccess = { panelName = null }, onFailure = { error = it.message })
                            saving = false
                        } }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_done)) }
                    }
                    HomePanel.Advanced -> {
                        DashboardInfo(stringResource(R.string.dashboard_mode), mode)
                        Text(stringResource(R.string.dashboard_install_warning))
                        Button(onClick = { panelName = null; actions.onInstallClick() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_flash)) }
                        ListItem(content = { Text(stringResource(R.string.dashboard_input_target)) }, supportingContent = { Text(stringResource(R.string.dashboard_target_note)) })
                        TextButton(onClick = { panelName = null; actions.onCheckInputs() }) { Text(stringResource(R.string.dashboard_check_only)) }
                        ListItem(content = { Text(stringResource(R.string.dashboard_production_loader)) }, trailingContent = { Icon(Icons.Outlined.CheckCircle, null) })
                        ListItem(content = { Text(stringResource(R.string.dashboard_loader)) }, supportingContent = { Text(stringResource(R.string.dashboard_experimental_gate)) })
                        FilledTonalButton(onClick = { panelName = null; actions.onOpenLoader() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.dashboard_loader)) }
                        TextButton(onClick = { open(HomePanel.Backups) }) { Text(stringResource(R.string.dashboard_backup)) }
                        TextButton(onClick = actions.onExportReport) { Text(stringResource(R.string.dashboard_export)) }
                    }
                    HomePanel.Backups -> {
                        Text(stringResource(R.string.dashboard_backup_note))
                        HomeBackupPanel(state.isFullFeatured)
                    }
                    HomePanel.Compatibility -> {
                        Text(stringResource(R.string.dashboard_environment), style = MaterialTheme.typography.titleMedium)
                        Text(state.envSummaryLine ?: unknown)
                        state.envReport?.items?.forEach { item -> DashboardInfo(item.title + " \u00b7 " + item.severity.name, item.detail) }
                        Text(stringResource(R.string.dashboard_input_target), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dashboard_target_note))
                        Text(stringResource(R.string.dashboard_validation), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dashboard_validation_note))
                        TextButton(onClick = actions.onExportReport) { Text(stringResource(R.string.dashboard_export)) }
                        TextButton(onClick = actions.onRefresh) { Text(stringResource(R.string.dashboard_refresh)) }
                    }
                    HomePanel.Versions -> { content(DashboardCard.Versions, DashboardSize.Expanded); HomeReleaseActions(state, actions) }
                    HomePanel.Todos -> {
                        if (todos.isEmpty()) Text(stringResource(R.string.dashboard_no_todos))
                        todos.forEach { todo -> TextButton(onClick = todo.open) { Column { Text(todo.title); if (todo.detail.isNotBlank()) Text(todo.detail) } } }
                    }
                    HomePanel.Shortcuts -> { shortcuts(DashboardSize.Expanded, true); TextButton(onClick = ::edit) { Text(stringResource(R.string.dashboard_edit)) } }
                    HomePanel.Runtime, HomePanel.Incidents -> {
                        content(DashboardCard.valueOf(panel.name), DashboardSize.Expanded)
                        Text(stringResource(R.string.dashboard_observation_note))
                        DashboardInfo(stringResource(R.string.dashboard_started), state.runtime.startedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: unknown)
                        state.runtimeError?.let { Text(stringResource(R.string.dashboard_runtime_error) + "\n" + it, color = MaterialTheme.colorScheme.error) }
                        state.runtime.events.reversed().forEach { DashboardInfo(stringResource(incidentTitle(it.code)), DateFormat.getDateTimeInstance().format(Date(it.at))) }
                        TextButton(onClick = actions.onExportReport) { Text(stringResource(R.string.dashboard_export)) }
                    }
                    else -> {
                        content(DashboardCard.valueOf(panel.name), DashboardSize.Expanded)
                        if (panel == HomePanel.Status || panel == HomePanel.Access) {
                            state.envReport?.items?.filter { it.group == EnvCheckGroup.Root || it.group == EnvCheckGroup.Version }?.forEach { DashboardInfo(it.title, it.detail) }
                            TextButton(onClick = actions.onOpenEnvCheck) { Text(stringResource(R.string.dashboard_diagnostics)) }
                        }
                        if (panel == HomePanel.Modules) HomeBatchRecoveryPanel(state.isFullFeatured && moduleKnown && !state.moduleBatchBusy)
                        if (panel == HomePanel.Modules) TextButton(onClick = { panelName = null; actions.onOpenModules() }) { Text(stringResource(R.string.dashboard_more)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    confirmation?.let { name ->
        val shortcut = DashboardShortcut.valueOf(name)
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(shortcut.titleId())) },
            text = { Text(stringResource(if (shortcut == DashboardShortcut.Reboot) R.string.dashboard_reboot_note else R.string.dashboard_batch_note) +
                if (shortcut == DashboardShortcut.DisableModules && modules.modules.any { it.metamodule && it.enabled }) "\n" + stringResource(R.string.dashboard_batch_meta_warning)
                else if (shortcut == DashboardShortcut.Reboot && state.isLateLoadMode) "\n" + stringResource(R.string.jailbreak_reboot_warning) else "") },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (shortcut) {
                    DashboardShortcut.Reboot -> actions.onReboot()
                    DashboardShortcut.DisableModules -> actions.onDisableModules()
                    DashboardShortcut.RestoreModules -> actions.onRestoreModules()
                    else -> Unit
                }
            }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
internal fun DashboardInfo(label: String, value: String) {
    SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { stringResource(R.string.dashboard_unknown) }, style = MaterialTheme.typography.bodyMedium)
    } }
}

/** A one-second clock invalidates only the time values, not the whole dashboard. */
@Composable
private fun DashboardRuntimeValues(state: HomeUiState, active: Boolean) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val inspection = LocalInspectionMode.current
    LaunchedEffect(active, owner) {
        if (active && !inspection) owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(1000) }
        }
    }
    Text(state.runtime.observedSince?.takeIf { state.loaded && state.runtimeError == null }?.let { duration(now - it) }
        ?: stringResource(R.string.dashboard_unknown), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.dashboard_uptime) + " \u00b7 " + duration(now), style = MaterialTheme.typography.bodySmall)
}
