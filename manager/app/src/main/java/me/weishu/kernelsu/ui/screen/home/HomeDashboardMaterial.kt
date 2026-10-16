package me.weishu.kernelsu.ui.screen.home

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
private fun DashboardSection.titleId() = when (this) {
    DashboardSection.Runtime -> R.string.dashboard_section_runtime
    DashboardSection.Actions -> R.string.dashboard_section_actions
    DashboardSection.Versions -> R.string.dashboard_section_versions
    DashboardSection.Device -> R.string.dashboard_section_device
}
private fun DashboardCard.descriptionId() = when (this) {
    DashboardCard.Status -> R.string.dashboard_description_status
    DashboardCard.Access -> R.string.dashboard_description_access
    DashboardCard.Runtime -> R.string.dashboard_description_runtime
    DashboardCard.Incidents -> R.string.dashboard_description_incidents
    DashboardCard.Todos -> R.string.dashboard_description_todos
    DashboardCard.Shortcuts -> R.string.dashboard_description_shortcuts
    DashboardCard.Versions -> R.string.dashboard_description_versions
    DashboardCard.Modules -> R.string.dashboard_description_modules
    DashboardCard.Device -> R.string.dashboard_description_device
    DashboardCard.Compatibility -> R.string.dashboard_description_compatibility
}
internal fun EnvCheckSeverity.labelId() = when (this) {
    EnvCheckSeverity.Pass -> R.string.env_check_pass
    EnvCheckSeverity.Warn -> R.string.env_check_warn
    EnvCheckSeverity.Fail -> R.string.env_check_fail
    EnvCheckSeverity.Unknown -> R.string.env_check_unknown
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
internal fun incidentTitle(code: String) = when (code) {
    "safe_mode" -> R.string.dashboard_safe_mode
    "root_unavailable" -> R.string.dashboard_root_missing
    "interface_mismatch" -> R.string.dashboard_interface_mismatch
    "activation_lost" -> R.string.dashboard_activation_lost
    else -> R.string.dashboard_unknown
}
private fun duration(millis: Long): String = android.text.format.DateUtils.formatElapsedTime(millis.coerceAtLeast(0) / 1000)
private val LocalDashboardActionsDisabled = staticCompositionLocalOf { false }
internal enum class HomePanel { EditShortcuts, Status, Access, Runtime, Incidents, Todos, Shortcuts, Versions, Modules, Device, Compatibility, Advanced, Backups }
internal data class HomeTodo(val title: String, val detail: String = "", val open: () -> Unit)

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
    var quickDraft by rememberSaveable { mutableStateOf("") }
    var quickLayout by rememberSaveable { mutableStateOf("") }
    var quickExpected by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var selectedCard by rememberSaveable { mutableStateOf(DashboardCard.Status.name) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showEditMenu by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val panel = panelName?.let { runCatching { HomePanel.valueOf(it) }.getOrNull() }
    val layout = if (editing) runCatching { DashboardLayout.parse(draft) }.getOrDefault(settings.layout) else settings.layout
    val selected = DashboardCard.entries.firstOrNull { it.name == selectedCard } ?: DashboardCard.Status
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    fun open(p: HomePanel) { panelName = p.name }
    fun edit() {
        selectedCard = settings.layout.cards.first().name
        draft = settings.layout.serialize(); base = settings.raw; error = null
        panelName = null; editing = true
    }
    fun cancelEdit() {
        if (!saving) { editing = false; panelName = null; error = null; showEditMenu = false }
    }
    fun saveLayout() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                HomeDashboardPreferences.save(base, layout)
                editing = false; panelName = null; error = null
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message
            } finally { saving = false }
        }
    }
    fun editShortcuts() {
        quickDraft = layout.shortcuts.joinToString(",") { it.name }
        quickLayout = layout.serialize(); quickExpected = settings.raw
        error = null; open(HomePanel.EditShortcuts)
    }
    fun locateCard(target: DashboardCard = DashboardCard.valueOf(selectedCard)) {
        val currentLayout = DashboardLayout.parse(draft)
        val rows = currentLayout.rows()
        val overview = rows.indexOfFirst { target in it }
        val fixed = listOf(DashboardCard.Todos, DashboardCard.Shortcuts).filter { it !in currentLayout.cards }
        val fixedIndex = currentLayout.rows(fixed).indexOfFirst { target in it }
        if (overview < 0 && fixedIndex < 0) return
        selectedCard = target.name
        val index = if (overview >= 0) 2 + overview else if (fixedIndex >= 0) 2 + rows.size + 1 + fixedIndex else 0
        scope.launch { listState.animateScrollToItem(index) }
    }
    BackHandler(enabled = editing && panel == null) { cancelEdit() }
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
    val todosKnown = state.loaded && moduleKnown && !state.envChecking && state.envReport != null && !state.huskyBusy
    val todos = buildList {
        if (state.loaded && !state.isManager) add(HomeTodo(stringResource(R.string.dashboard_permission_missing), stringResource(R.string.home_sheet_no_access_note)) { open(HomePanel.Access) })
        if (state.pendingReboot) add(HomeTodo(stringResource(R.string.reboot_to_apply), open = { open(HomePanel.Modules) }))
        state.pendingRebootError?.let { add(HomeTodo(stringResource(R.string.home_sheet_reboot_record_error), it) { open(HomePanel.Modules) }) }
        if (state.huskyUpdateStatus in listOf(HuskyUpdateStatus.Error, HuskyUpdateStatus.NoMatchingLkm)) add(HomeTodo(stringResource(R.string.home_sheet_update_failed), open = { open(HomePanel.Versions) }))
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
        items.chunked(if (size == DashboardSize.Compact || LocalDensity.current.fontScale > 1.3f) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { shortcut ->
                    val privileged = shortcut in listOf(DashboardShortcut.Reboot, DashboardShortcut.DisableModules, DashboardShortcut.RestoreModules)
                    FilledTonalButton(onClick = { runShortcut(shortcut) }, shapes = ButtonDefaults.shapes(),
                        enabled = !LocalDashboardActionsDisabled.current && (!privileged || state.operationBlockReason(allowSafeMode = shortcut == DashboardShortcut.Reboot) == null) && (shortcut != DashboardShortcut.CheckUpdates || !state.huskyBusy), modifier = Modifier.weight(1f)) {
                        Text(stringResource(shortcut.titleId()))
                    }
                }

            }
        }
        if (layout.shortcuts.size > items.size) TextButton(enabled = !LocalDashboardActionsDisabled.current, onClick = { open(HomePanel.Shortcuts) }) { Text(stringResource(R.string.dashboard_more)) }
        state.operationBlockReason()?.let { Text(stringResource(it.textId()), style = MaterialTheme.typography.bodySmall) }
        if (DashboardShortcut.CheckUpdates in items) HomeUpdateNotice(state, actions, showCheckAction = false, enabled = !LocalDashboardActionsDisabled.current)
    }
    @Composable fun content(card: DashboardCard, size: DashboardSize) {
        val compact = size == DashboardSize.Compact
        val expanded = size == DashboardSize.Expanded
        when (card) {
            DashboardCard.Status -> {
                Text(stringResource(state.activationTitleId()), style = MaterialTheme.typography.headlineSmall)
                if (state.isSafeMode) Text(stringResource(R.string.dashboard_safe_mode))
                else Text(state.systemInfo.deviceModel, style = MaterialTheme.typography.bodyMedium)
                if (!compact) Text(if (state.isSafeMode) stringResource(R.string.dashboard_safe_mode_note) else state.envReport?.items?.firstOrNull { it.group == EnvCheckGroup.Root && it.severity != EnvCheckSeverity.Pass }?.detail.orEmpty(),
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            DashboardCard.Access -> {
                Text(stringResource(if (!state.loaded) R.string.dashboard_loading else if (state.isManager) R.string.dashboard_permission_ok else R.string.dashboard_permission_missing), style = MaterialTheme.typography.titleLarge)
                Text(if (state.lkmMode == null) stringResource(R.string.dashboard_mode_unknown) else mode, style = MaterialTheme.typography.bodyMedium)
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
                if (todos.isEmpty()) Text(stringResource(if (todosKnown) R.string.dashboard_no_todos else R.string.home_sheet_tasks_incomplete))
                else {
                    todos.take(if (compact) 1 else if (expanded) 20 else 3).forEach { todo ->
                        TextButton(enabled = !LocalDashboardActionsDisabled.current, onClick = todo.open, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                            Text(todo.title, style = MaterialTheme.typography.titleSmall)
                            if (!compact && todo.detail.isNotBlank()) Text(todo.detail, style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                    if (todos.size > if (compact) 1 else if (expanded) 20 else 3) TextButton(enabled = !LocalDashboardActionsDisabled.current, onClick = { open(HomePanel.Todos) }) { Text(stringResource(R.string.dashboard_more)) }
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
                    Button(enabled = !LocalDashboardActionsDisabled.current, onClick = { open(HomePanel.Advanced) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.dashboard_flash)) }
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
                    if (compact) Text(stringResource(if (state.isSafeMode) R.string.dashboard_module_counts_safe else R.string.dashboard_module_counts, enabled, disabled), style = MaterialTheme.typography.bodyMedium)
                    else {
                        info(if (state.isSafeMode) R.string.dashboard_configured_enabled else R.string.dashboard_enabled, enabled.toString())
                        info(R.string.dashboard_disabled, disabled.toString())
                    }
                    if (!compact) {
                        info(R.string.home_sheet_module_file_changes, pending.toString())
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
                Text(stringResource(state.compatibilityTitleId()), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.home_sheet_compatibility_scope), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(stringResource(R.string.dashboard_validation_note), style = MaterialTheme.typography.bodySmall)
                if (expanded) {
                    Text(stringResource(R.string.dashboard_target_note))
                    state.envReport?.items?.take(3)?.forEach { DashboardInfo(it.title, it.detail) }
                }
            }
        }
    }
    @Composable fun card(card: DashboardCard, size: DashboardSize, modifier: Modifier = Modifier) {
        val alert = card == DashboardCard.Status && state.activation == HomeActivation.Attention
        val container = when {
            alert -> MaterialTheme.colorScheme.errorContainer
            card == DashboardCard.Status && state.activation != HomeActivation.Active -> MaterialTheme.colorScheme.surfaceContainerHigh
            card == DashboardCard.Status -> MaterialTheme.colorScheme.primaryContainer
            card == DashboardCard.Access -> MaterialTheme.colorScheme.secondaryContainer
            card == DashboardCard.Runtime -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            border = if (editing && selected == card) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors = CardDefaults.cardColors(containerColor = container)) {
            val selectionModifier = if (editing) Modifier.selectable(selected = selected == card, enabled = !saving,
                role = Role.RadioButton, onClick = { selectedCard = card.name })
            else Modifier.clickable(onClick = { open(HomePanel.valueOf(card.name)) })
            Column(Modifier.fillMaxWidth().then(selectionModifier).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(card.icon(), null, Modifier.size(20.dp))
                    Text(stringResource(card.titleId()), style = MaterialTheme.typography.labelLarge)
                }
                CompositionLocalProvider(LocalDashboardActionsDisabled provides editing) { content(card, size) }
            }
            if (editing && card == DashboardCard.Shortcuts) TextButton(enabled = !saving, onClick = ::editShortcuts,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) { Text(stringResource(R.string.dashboard_edit_shortcuts)) }
        }
    }
    @Composable fun cardRow(row: List<DashboardCard>, columns: Int) {
        if (columns == 2 && row.size == 2) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card(it, layout.sizes.getValue(it), Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card(it, layout.sizes.getValue(it)) }
            }
        }
    }
    BoxWithConstraints {
        val columns = if (maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f) 2 else 1
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            if (editing) {
                Column {
                    TopAppBar(title = { Text(stringResource(R.string.home_sheet_edit_selected, stringResource(selected.titleId())), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(enabled = !saving, onClick = ::cancelEdit) { Icon(Icons.Outlined.Close, stringResource(android.R.string.cancel)) }
                        }, actions = {
                            TextButton(enabled = !saving, onClick = ::saveLayout) {
                                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text(stringResource(R.string.dashboard_save))
                            }
                            Box {
                                IconButton(enabled = !saving, onClick = { showEditMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.dashboard_more)) }
                                DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_edit_shortcuts)) }, onClick = {
                                        showEditMenu = false; editShortcuts()
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.home_sheet_locate_card)) }, onClick = { showEditMenu = false; locateCard() })
                                val selectedIndex = layout.cards.indexOf(selected)
                                listOf(-1, 1).forEach { offset ->
                                    DropdownMenuItem(enabled = selectedIndex >= 0 && selectedIndex + offset in layout.cards.indices,
                                        text = { Text(stringResource(if (offset < 0) R.string.dashboard_move_up else R.string.dashboard_move_down)) }, onClick = {
                                            val cards = layout.cards.toMutableList(); java.util.Collections.swap(cards, selectedIndex, selectedIndex + offset)
                                            draft = layout.copy(cards = cards).serialize(); showEditMenu = false
                                        })
                                }
                                DropdownMenuItem(enabled = selectedIndex >= 0 && selected != DashboardCard.Compatibility,
                                    text = { Text(stringResource(R.string.dashboard_remove_card)) }, onClick = {
                                        draft = layout.copy(cards = layout.cards - selected).serialize()
                                        selectedCard = layout.cards.first { it != selected }.name; showEditMenu = false
                                    })
                                DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_reset)) }, onClick = {
                                        draft = DashboardLayout().serialize(); selectedCard = DashboardCard.Status.name; showEditMenu = false
                                    })
                                }
                            }
                        })
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        DashboardSize.entries.forEachIndexed { index, candidate ->
                            SegmentedButton(selected = layout.sizes[selected] == candidate, enabled = !saving,
                                onClick = { draft = layout.copy(sizes = layout.sizes + (selected to candidate)).serialize() },
                                shape = SegmentedButtonDefaults.itemShape(index, DashboardSize.entries.size)) {
                                Text(stringResource(when (candidate) {
                                    DashboardSize.Compact -> R.string.dashboard_size_small
                                    DashboardSize.Standard -> R.string.dashboard_size_medium
                                    DashboardSize.Expanded -> R.string.dashboard_size_large
                                }))
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp), maxLines = 2) }
                }
            } else {
                TopAppBar(title = { Text("HuskySU", style = MaterialTheme.typography.headlineSmall) }, scrollBehavior = scroll, actions = {
                    IconButton(onClick = actions.onRefresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.dashboard_refresh)) }
                    IconButton(onClick = ::edit) { Icon(Icons.Outlined.Edit, stringResource(R.string.dashboard_edit)) }
                })
            }
        }) { padding ->
            LazyColumn(state = listState, modifier = Modifier.padding(padding).nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInnerPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing) item(key = "edit_hint") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dashboard_inline_hint), style = MaterialTheme.typography.bodyMedium)
                        if (settings.invalid) Text(stringResource(R.string.dashboard_settings_invalid), color = MaterialTheme.colorScheme.error)
                    }
                }
                item(key = "overview_heading") {
                    DashboardHeading(R.string.dashboard_overview)
                }
                layout.rows().forEach { row ->
                    item(key = row.first().name) { cardRow(row, columns) }
                }
                val actionCards = listOf(DashboardCard.Todos, DashboardCard.Shortcuts).filter { it !in layout.cards }
                if (actionCards.isNotEmpty()) item(key = "actions_heading") {
                    DashboardHeading(R.string.dashboard_section_actions)
                }
                layout.rows(actionCards).forEach { row ->
                    item(key = row.first().name) { cardRow(row, columns) }
                }
                if (editing) {
                    item(key = "library_heading") {
                        DashboardHeading(R.string.dashboard_card_library)
                    }
                    DashboardSection.entries.forEach { section ->
                        item(key = "library_" + section.name) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column {
                                    Text(stringResource(section.titleId()), style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(16.dp).semantics { heading() })
                                    section.cards.forEach { candidate ->
                                        val candidateTitle = stringResource(candidate.titleId())
                                        val added = candidate in layout.cards
                                        ListItem(content = { Text(stringResource(candidate.titleId())) },
                                            supportingContent = { Text(stringResource(candidate.descriptionId())) },
                                            leadingContent = { DashboardCategoryIcon(candidate) },
                                            trailingContent = {
                                                IconButton(enabled = !saving && !added, onClick = {
                                                    draft = layout.copy(cards = layout.cards + candidate).serialize()
                                                    selectedCard = candidate.name
                                                    scope.launch {
                                                        val response = snackbar.showSnackbar(message = me.weishu.kernelsu.ksuApp.getString(R.string.home_sheet_card_added, candidateTitle), actionLabel = me.weishu.kernelsu.ksuApp.getString(R.string.home_sheet_locate_card))
                                                        if (response == SnackbarResult.ActionPerformed) locateCard(candidate)
                                                    }
                                                }) { Icon(if (added) Icons.Outlined.Check else Icons.Outlined.Add,
                                                    stringResource(if (added) R.string.dashboard_card_added else R.string.dashboard_add_card)) }
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
                if (!editing) {
                    item(key = "details_heading") { DashboardHeading(R.string.dashboard_all_information) }
                    item(key = "information_categories") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column {
                                listOf(DashboardCard.Status, DashboardCard.Access, DashboardCard.Runtime, DashboardCard.Incidents,
                                    DashboardCard.Versions, DashboardCard.Modules, DashboardCard.Device, DashboardCard.Compatibility).forEach { entry ->
                                    ListItem(content = { Text(stringResource(entry.titleId())) },
                                        supportingContent = { Text(stringResource(entry.descriptionId())) },
                                        leadingContent = { DashboardCategoryIcon(entry) },
                                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                                        modifier = Modifier.clickable { open(HomePanel.valueOf(entry.name)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (panel == HomePanel.EditShortcuts) {
        val quickBase = runCatching { DashboardLayout.parse(quickLayout) }.getOrDefault(layout)
        val shortcuts = runCatching { quickDraft.split(',').map(DashboardShortcut::valueOf) }.getOrNull()
            ?.takeIf { it.size in 1..6 && it.distinct().size == it.size } ?: quickBase.shortcuts
        val quick = quickBase.copy(shortcuts = shortcuts)
        DashboardShortcutEditor(quick, saving, error,
            doneLabel = stringResource(if (editing) R.string.home_sheet_apply_draft else R.string.dashboard_save),
            onChange = { quickDraft = it.joinToString(",") { shortcut -> shortcut.name } },
            onDismiss = { if (!saving) { panelName = null; error = null } }, onSave = {
                if (editing) { draft = layout.copy(shortcuts = quick.shortcuts).serialize(); panelName = null }
                else if (!saving) { saving = true; scope.launch {
                    try { HomeDashboardPreferences.save(if (quickLayout.isEmpty()) settings.raw else quickExpected, quick); panelName = null; error = null }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message }
                    finally { saving = false }
                } }
            })
    } else if (panel == HomePanel.Backups) {
        HomeBackupSheet(state.operationBlockReason() == null, onDismiss = { panelName = null })
    } else if (panel != null) {
        val primary = when (panel) {
            HomePanel.Status, HomePanel.Access, HomePanel.Compatibility, HomePanel.Todos -> R.string.home_sheet_recheck
            HomePanel.Versions -> R.string.dashboard_flash
            HomePanel.Modules -> R.string.dashboard_more
            HomePanel.Runtime -> R.string.dashboard_incidents
            HomePanel.Advanced -> R.string.dashboard_flash
            else -> null
        }
        HomeInformationSheet(panel, stringResource(DashboardCard.entries.firstOrNull { it.name == panel.name }?.titleId() ?: R.string.dashboard_advanced),
            onDismiss = { panelName = null }, onReport = if (panel == HomePanel.Shortcuts) null else actions.onExportReport,
            footer = if (primary == null) null else ({
                Button(enabled = !state.envChecking || panel !in listOf(HomePanel.Status, HomePanel.Access, HomePanel.Compatibility, HomePanel.Todos),
                    onClick = {
                        when (panel) {
                            HomePanel.Modules -> { panelName = null; actions.onOpenModules() }
                            HomePanel.Runtime -> open(HomePanel.Incidents)
                            HomePanel.Versions, HomePanel.Advanced -> { panelName = null; actions.onInstallClick() }
                            else -> actions.onRefresh()
                        }
                    }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(primary)) }
            })) {
            val detailActions = actions.copy(
                onCheckInputs = { panelName = null; actions.onCheckInputs() },
                onOpenLoader = { panelName = null; actions.onOpenLoader() },
                onInstallInactiveSlot = { panelName = null; actions.onInstallInactiveSlot() },
                onUpdateLkm = { panelName = null; actions.onUpdateLkm() })
            HomeDetailContent(panel, state, detailActions, modules, todos, todosKnown, ::open,
                shortcuts = { shortcuts(DashboardSize.Expanded, true) }, editShortcuts = ::editShortcuts)
        }
    }
    confirmation?.let { name ->
        val shortcut = DashboardShortcut.valueOf(name)
        var reviewed by remember(name) { mutableStateOf<ModuleBatchReview?>(null) }
        var reviewFailure by remember(name) { mutableStateOf<String?>(null) }
        LaunchedEffect(name) {
            if (shortcut != DashboardShortcut.Reboot) HomeModuleBatch.review(shortcut == DashboardShortcut.RestoreModules)
                .fold(onSuccess = { reviewed = it }, onFailure = { reviewFailure = it.message })
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(shortcut.titleId())) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shortcut == DashboardShortcut.Reboot) {
                        Text(stringResource(R.string.dashboard_reboot_note))
                        if (state.isLateLoadMode) Text(stringResource(R.string.jailbreak_reboot_warning))
                    } else {
                        Text(stringResource(R.string.dashboard_batch_note))
                        if (reviewed == null && reviewFailure == null) LoadingIndicator()
                        reviewFailure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        reviewed?.let { review ->
                            Text(stringResource(R.string.home_sheet_batch_count, review.targets.size, review.skipped.size), style = MaterialTheme.typography.titleMedium)
                            review.targets.forEach { Text(it.name + " · " + it.version) }
                            if (review.targets.any { it.metamodule }) Text(stringResource(R.string.dashboard_batch_meta_warning), color = MaterialTheme.colorScheme.error)
                            if (review.skipped.isNotEmpty()) HomeFacts(stringResource(R.string.home_sheet_skipped), review.skipped.map { it to stringResource(R.string.home_sheet_skipped_note) })
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = { TextButton(enabled = state.operationBlockReason(allowSafeMode = shortcut == DashboardShortcut.Reboot) == null &&
                (shortcut == DashboardShortcut.Reboot || reviewed?.targets?.isNotEmpty() == true), onClick = {
                    confirmation = null
                    if (shortcut == DashboardShortcut.Reboot) actions.onReboot() else reviewed?.let(actions.onApplyModuleBatch)
                }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
private fun DashboardCategoryIcon(card: DashboardCard) {
    val color = when (card) {
        DashboardCard.Status, DashboardCard.Access -> MaterialTheme.colorScheme.primaryContainer
        DashboardCard.Device, DashboardCard.Compatibility -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color) {
        Icon(card.icon(), null, Modifier.padding(10.dp).size(24.dp))
    }
}

@Composable
private fun DashboardHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp).semantics { heading() })
}

@Composable
private fun DashboardShortcutEditor(layout: DashboardLayout, saving: Boolean, error: String?,
                                    doneLabel: String, onChange: (List<DashboardShortcut>) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    ModalBottomSheet(sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dashboard_edit_shortcuts), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(enabled = !saving, onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dashboard_shortcuts_note), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Button(enabled = !saving, onClick = onSave, shapes = ButtonDefaults.shapes()) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(doneLabel)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(layout.shortcuts + DashboardShortcut.entries.filter { it !in layout.shortcuts }, key = { it.name }) { shortcut ->
                    val index = layout.shortcuts.indexOf(shortcut)
                    val canToggle = !saving && (index >= 0 || layout.shortcuts.size < 6) && !(index >= 0 && layout.shortcuts.size == 1)
                    ListItem(content = { Text(stringResource(shortcut.titleId())) },
                        modifier = Modifier.toggleable(value = index >= 0, enabled = canToggle, role = Role.Checkbox, onValueChange = { checked ->
                            onChange(if (checked) layout.shortcuts + shortcut else layout.shortcuts - shortcut)
                        }),
                        leadingContent = { Checkbox(checked = index >= 0, onCheckedChange = null, enabled = canToggle) },
                        trailingContent = {
                            if (index >= 0) Row {
                                fun move(delta: Int) {
                                    val shortcuts = layout.shortcuts.toMutableList()
                                    java.util.Collections.swap(shortcuts, index, index + delta); onChange(shortcuts)
                                }
                                IconButton(enabled = !saving && index > 0, onClick = { move(-1) }) {
                                    Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.dashboard_move_up))
                                }
                                IconButton(enabled = !saving && index < layout.shortcuts.lastIndex, onClick = { move(1) }) {
                                    Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.dashboard_move_down))
                                }
                            }
                        })
                }
            }
        }
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
internal fun DashboardRuntimeValues(state: HomeUiState, active: Boolean, hero: Boolean = false) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val inspection = LocalInspectionMode.current
    LaunchedEffect(active, owner) {
        if (active && !inspection) owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(1000) }
        }
    }
    val value = state.runtime.observedSince?.takeIf { state.loaded && state.runtimeError == null }?.let { duration(now - it) }
        ?: stringResource(R.string.dashboard_unknown)
    if (hero) HomeStateCard(value, stringResource(R.string.dashboard_observation_note), Icons.Outlined.Schedule,
        if (state.runtime.observedSince != null && state.runtimeError == null) HomeTone.Primary else HomeTone.Neutral)
    else Text(value, style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.dashboard_uptime) + " \u00b7 " + duration(now), style = MaterialTheme.typography.bodySmall)
}
