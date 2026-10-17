package me.weishu.kernelsu.ui.screen.home

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.*
import me.weishu.kernelsu.ui.screen.module.ModuleUiState
import java.text.DateFormat
import java.util.Date

internal fun HomeBlockReason.textId() = when (this) {
    HomeBlockReason.Checking -> R.string.home_sheet_checking
    HomeBlockReason.Authorization -> R.string.dashboard_permission_missing
    HomeBlockReason.Interface -> R.string.dashboard_interface_mismatch
    HomeBlockReason.Root -> R.string.dashboard_root_missing
    HomeBlockReason.SafeMode -> R.string.dashboard_safe_mode
    HomeBlockReason.Busy -> R.string.dashboard_module_busy
}
internal fun HomeUiState.activationTitleId() = when (activation) {
    HomeActivation.Checking -> R.string.home_sheet_checking
    HomeActivation.Unknown -> R.string.home_sheet_activation_unknown
    HomeActivation.Attention -> R.string.home_sheet_attention
    HomeActivation.Active -> R.string.dashboard_active
}
internal fun HomeUiState.activationExplanationId() = when {
    !loaded -> R.string.home_sheet_checking_note
    !isManager -> R.string.home_sheet_no_access_note
    ksuVersion == null -> R.string.home_sheet_driver_unknown_note
    isSafeMode -> R.string.dashboard_safe_mode_note
    operationBlockReason() != null -> operationBlockReason()!!.textId()
    else -> R.string.home_sheet_active_note
}
internal fun HomeUiState.compatibilityTitleId() = when {
    !loaded -> R.string.home_sheet_checking
    requiresNewKernel || showVersionMismatchWarning -> R.string.dashboard_interface_mismatch
    !isManager || kernelUAPIVersion == null -> if (envChecking) R.string.home_sheet_checking else R.string.dashboard_compat_unknown
    kernelUAPIVersion == managerUAPIVersion -> R.string.home_sheet_interface_matched
    else -> R.string.dashboard_compat_unknown
}
private fun formatTime(time: Long) = DateFormat.getDateTimeInstance().format(Date(time))

@Composable
internal fun HomeUpdateNotice(state: HomeUiState, actions: HomeActions, showCheckAction: Boolean = true, enabled: Boolean = true) {
    val title = stringResource(when (state.huskyUpdateStatus) {
        HuskyUpdateStatus.Idle -> R.string.home_sheet_update_idle
        HuskyUpdateStatus.Checking -> R.string.home_sheet_update_checking
        HuskyUpdateStatus.Downloading -> R.string.home_sheet_update_downloading
        HuskyUpdateStatus.Available -> R.string.dashboard_update_driver
        HuskyUpdateStatus.UpToDate -> R.string.home_sheet_update_latest
        HuskyUpdateStatus.NoMatchingLkm -> R.string.home_sheet_update_no_target
        HuskyUpdateStatus.Error -> R.string.home_sheet_update_failed
        HuskyUpdateStatus.MetadataError -> R.string.home_sheet_update_metadata_failed
        HuskyUpdateStatus.DownloadError -> R.string.husky_lkm_download_failed
    })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (state.huskyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.home_sheet_update_scope), style = MaterialTheme.typography.bodySmall)
            state.huskyRelease?.let { Text(it.tag, style = MaterialTheme.typography.bodyMedium) }
            state.huskyError?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (enabled && showCheckAction && !state.huskyBusy) TextButton(onClick = actions.onCheckHuskyUpdate) {
                Text(stringResource(if (state.huskyUpdateStatus in listOf(HuskyUpdateStatus.Error, HuskyUpdateStatus.MetadataError, HuskyUpdateStatus.DownloadError))
                    R.string.home_sheet_retry else R.string.dashboard_check_updates))
            }
            if (enabled) state.huskyDiagnostic?.let { diagnostic -> HomeTechnicalDetails(diagnostic) {
                androidx.compose.foundation.text.selection.SelectionContainer { Text(diagnostic, style = MaterialTheme.typography.bodySmall) }
            } }

        }
    }
}

@Composable
internal fun HomeDetailContent(panel: HomePanel, state: HomeUiState, actions: HomeActions,
                               modules: ModuleUiState, todos: List<HomeTodo>, todosKnown: Boolean,
                               onPanel: (HomePanel) -> Unit, shortcuts: @Composable () -> Unit,
                               editShortcuts: () -> Unit) {
    val unknown = stringResource(R.string.dashboard_unknown)
    val mode = when (state.lkmMode) { true -> "LKM"; false -> stringResource(R.string.home_sheet_builtin); null -> unknown }
    val knownModules = modules.hasLoaded && modules.loadError == null && state.canReadModules
    val uapiText = stringResource(R.string.dashboard_uapi_versions, state.kernelUAPIVersion?.toString() ?: unknown, state.managerUAPIVersion.toString())
    @Composable fun facts(title: Int, vararg values: Pair<Int, String>) {
        HomeFacts(stringResource(title), values.map { stringResource(it.first) to it.second })
    }
    when (panel) {
        HomePanel.Status, HomePanel.Access -> {
            HomeStateCard(stringResource(if (panel == HomePanel.Status) state.activationTitleId() else if (!state.loaded) R.string.home_sheet_checking else if (state.activation == HomeActivation.Attention) R.string.home_sheet_attention else if (state.isManager) R.string.dashboard_permission_ok else R.string.dashboard_permission_missing),
                stringResource(state.activationExplanationId()), when (state.activation) { HomeActivation.Active -> Icons.Outlined.CheckCircle; HomeActivation.Attention -> Icons.Outlined.Warning; HomeActivation.Unknown -> Icons.AutoMirrored.Outlined.HelpOutline; HomeActivation.Checking -> Icons.Outlined.Sync },
                when (state.activation) { HomeActivation.Active -> HomeTone.Primary; HomeActivation.Attention -> HomeTone.Error; else -> HomeTone.Neutral })
            if (!state.loaded) LoadingIndicator()
            if (panel == HomePanel.Status && state.canUpgradeKernelModule) {
                Text(stringResource(R.string.home_upgrade_kernel_module_note), style = MaterialTheme.typography.bodySmall)
                Button(onClick = actions.onUpgradeKernelModule, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_upgrade_kernel_module)) }
            }
            facts(R.string.home_sheet_key_checks,
                R.string.dashboard_access to if (!state.loaded) unknown else stringResource(if (state.isManager) R.string.dashboard_permission_ok else R.string.dashboard_permission_missing),
                R.string.dashboard_driver to (state.ksuVersion?.toString() ?: unknown),
                R.string.home_sheet_root_probe to if (!state.loaded) unknown else stringResource(if (state.isRootAvailable) R.string.dashboard_root_ok else R.string.dashboard_root_missing),
                R.string.dashboard_mode to mode)
            if (state.isLateLoadMode) Text(stringResource(R.string.jailbreak_reboot_warning))
            if (state.loaded) Text(stringResource(R.string.dashboard_checked_at) + " · " + (state.observedWallTime.takeIf { it > 0 }?.let(::formatTime) ?: unknown), style = MaterialTheme.typography.bodySmall)
            HomeTechnicalDetails(panel) {
                facts(R.string.home_sheet_interface, R.string.home_sheet_manager_uapi to state.managerUAPIVersion.toString(),
                    R.string.home_sheet_driver_uapi to (state.kernelUAPIVersion?.toString() ?: unknown))
                state.envReport?.items?.filter { it.group == EnvCheckGroup.Root }?.let { items -> HomeFacts(stringResource(R.string.dashboard_environment), items.map { it.title to it.detail }) }
            }
        }
        HomePanel.Runtime -> {
            DashboardRuntimeValues(state, true, hero = true)
            facts(R.string.home_sheet_record_scope, R.string.dashboard_started to (state.runtime.startedAt?.let(::formatTime) ?: unknown))
            state.runtimeError?.let { HomeStateCard(stringResource(R.string.dashboard_runtime_error), it, Icons.Outlined.Warning, HomeTone.Error) }
        }
        HomePanel.Incidents -> {
            HomeStateCard(if (!state.loaded || state.runtimeError != null) unknown else state.runtime.total.toString(), stringResource(R.string.home_sheet_incident_scope), Icons.Outlined.History, HomeTone.Secondary)
            if (state.loaded && state.runtimeError == null && state.runtime.events.isEmpty()) Text(stringResource(R.string.dashboard_no_incidents))
            state.runtime.events.reversed().forEach { event ->
                ListItem(content = { Text(stringResource(incidentTitle(event.code))) }, supportingContent = { Text(formatTime(event.at)) }, leadingContent = { Icon(Icons.Outlined.Info, null) })
            }
            state.runtimeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        HomePanel.Todos -> {
            HomeStateCard(todos.size.toString(), stringResource(if (todosKnown) R.string.home_sheet_tasks_known else R.string.home_sheet_tasks_incomplete), Icons.Outlined.Checklist, HomeTone.Secondary)
            if (todos.isEmpty()) Text(stringResource(if (todosKnown) R.string.dashboard_no_todos else R.string.home_sheet_tasks_incomplete))
            todos.forEach { todo -> ListItem(content = { Text(todo.title) }, supportingContent = { if (todo.detail.isNotBlank()) Text(todo.detail) }, leadingContent = { Icon(Icons.Outlined.Info, null) }, modifier = Modifier.clickable(onClick = todo.open)) }
        }
        HomePanel.Versions -> {
            HomeStateCard(state.ksuVersion?.toString() ?: unknown, stringResource(R.string.dashboard_driver) + " · " + mode, Icons.Outlined.SystemUpdate, if (state.ksuVersion == null) HomeTone.Neutral else HomeTone.Primary)
            facts(R.string.dashboard_versions, R.string.dashboard_manager to state.systemInfo.managerVersion,
                R.string.dashboard_driver to (state.ksuVersion?.toString() ?: unknown))
            HomeUpdateNotice(state, actions)
            if (driverVersionStatus(state.currentManagerVersionCode, state.ksuVersion, state.managerUAPIVersion, state.kernelUAPIVersion, state.requiresNewKernel) == DriverVersionStatus.BuildDifferent) Text(stringResource(R.string.dashboard_build_difference))
            if (state.hasUpdate) FilledTonalButton(onClick = { actions.onOpenUrl(state.latestVersionInfo.downloadUrl) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_update_manager)) }
            if (state.huskyUpdateStatus == HuskyUpdateStatus.Available && state.lkmMode == true) FilledTonalButton(enabled = state.operationBlockReason(repair = true) == null, onClick = actions.onUpdateLkm, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.husky_update_lkm)) }
            if (state.canUpgradeKernelModule) {
                Text(uapiText, style = MaterialTheme.typography.bodySmall)
                Button(onClick = actions.onUpgradeKernelModule, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_upgrade_kernel_module)) }
            }
            if (state.lkmMode == false) Text(stringResource(R.string.dashboard_gki_source_note))
            if (state.huskyRelease != null) {
                TextButton(onClick = actions.onOpenHuskyRelease) { Text(stringResource(R.string.husky_open_release)) }
                if (state.huskyUpdateStatus in listOf(HuskyUpdateStatus.Available, HuskyUpdateStatus.UpToDate)) TextButton(enabled = !state.huskyBusy, onClick = actions.onDownloadLkmToDownloads) { Text(stringResource(R.string.husky_download_lkm)) }
            }
            if (state.showOtaSlotCard) FilledTonalButton(enabled = state.operationBlockReason(repair = true) == null, onClick = actions.onInstallInactiveSlot) { Text(stringResource(R.string.home_sheet_ota)) }
            HomeTechnicalDetails(panel) { facts(R.string.home_sheet_interface, R.string.home_sheet_manager_uapi to state.managerUAPIVersion.toString(), R.string.home_sheet_driver_uapi to (state.kernelUAPIVersion?.toString() ?: unknown)) }
        }
        HomePanel.Modules -> {
            HomeStateCard(if (knownModules) modules.modules.size.toString() else unknown, stringResource(R.string.dashboard_module_note), Icons.Outlined.Extension)
            if (knownModules) facts(R.string.dashboard_modules,
                (if (state.isSafeMode) R.string.dashboard_configured_enabled else R.string.dashboard_enabled) to modules.modules.count { it.enabled && !it.remove }.toString(),
                R.string.dashboard_disabled to modules.modules.count { !it.enabled && !it.remove }.toString(),
                R.string.home_sheet_module_file_changes to modules.modules.count { it.update || it.remove }.toString(),
                R.string.module_update to modules.updateInfo.count { it.value.downloadUrl.isNotBlank() }.toString(),
                R.string.dashboard_metamodule to (modules.modules.firstOrNull { it.metamodule }?.name ?: unknown))
            if (state.pendingReboot) Text(stringResource(R.string.reboot_to_apply), color = MaterialTheme.colorScheme.primary)
            modules.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HomeBatchRecoveryPanel(state.operationBlockReason() == null && knownModules)
        }
        HomePanel.Device -> {
            HomeStateCard(state.systemInfo.deviceModel, "Android ${Build.VERSION.RELEASE}", Icons.Outlined.Smartphone)
            facts(R.string.dashboard_device, R.string.dashboard_android to "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                R.string.dashboard_build to Build.ID, R.string.dashboard_kernel to state.systemInfo.kernelVersion,
                R.string.dashboard_architecture to Build.SUPPORTED_ABIS.joinToString(),
                R.string.dashboard_page_size to (state.pageSize?.let { "$it B" } ?: state.pageSizeError?.let { stringResource(R.string.dashboard_page_size_failed, it) } ?: unknown), R.string.dashboard_slot to (state.slot?.ifBlank { unknown } ?: unknown))
            facts(R.string.home_sheet_security, R.string.dashboard_selinux to state.systemInfo.selinuxStatus,
                R.string.dashboard_seccomp to state.systemInfo.seccompStatus.takeIf { it >= 0 }?.toString().orEmpty())
            HomeTechnicalDetails(panel) { facts(R.string.home_sheet_technical_details, R.string.dashboard_fingerprint to state.systemInfo.fingerprint) }
        }
        HomePanel.Compatibility -> {
            HomeStateCard(stringResource(state.compatibilityTitleId()), stringResource(R.string.home_sheet_compatibility_scope), Icons.Outlined.VerifiedUser,
                if (state.requiresNewKernel || state.showVersionMismatchWarning) HomeTone.Error else HomeTone.Neutral)
            HomeFacts(stringResource(R.string.dashboard_interface), listOf(stringResource(R.string.dashboard_interface) to uapiText))
            if (state.canUpgradeKernelModule) FilledTonalButton(onClick = actions.onUpgradeKernelModule, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_upgrade_kernel_module)) }
            if (state.envChecking) LoadingIndicator()
            state.envReport?.let { report ->
                Text(stringResource(R.string.dashboard_checked_at) + " · " + formatTime(report.generatedAtEpochMs), style = MaterialTheme.typography.bodySmall)
                EnvCheckGroup.entries.forEach { group ->
                    val entries = report.items.filter { it.group == group }
                    if (entries.isNotEmpty()) HomeFacts(stringResource(when (group) {
                        EnvCheckGroup.Root -> R.string.dashboard_access
                        EnvCheckGroup.Version -> R.string.dashboard_versions
                        EnvCheckGroup.Boot -> R.string.home_sheet_boot_environment
                        EnvCheckGroup.IntegrityOnline -> R.string.home_sheet_online_checks
                        EnvCheckGroup.Modules -> R.string.dashboard_modules
                        EnvCheckGroup.System -> R.string.dashboard_device
                    }), entries.map { (it.title + " · " + stringResource(it.severity.labelId())) to it.detail })
                }
            } ?: Text(state.envSummaryLine ?: unknown)
            facts(R.string.dashboard_validation, R.string.dashboard_input_target to stringResource(R.string.dashboard_target_note),
                R.string.home_sheet_device_evidence to stringResource(R.string.dashboard_validation_note))
            TextButton(onClick = { actions.onCheckInputs() }) { Text(stringResource(R.string.dashboard_check_only)) }
        }
        HomePanel.Shortcuts -> {
            HomeStateCard(stringResource(R.string.dashboard_shortcuts), stringResource(R.string.dashboard_shortcuts_note), Icons.Outlined.Bolt)
            shortcuts()
            TextButton(onClick = editShortcuts) { Text(stringResource(R.string.dashboard_edit_shortcuts)) }
        }
        HomePanel.Advanced -> {
            HomeStateCard(stringResource(R.string.home_sheet_install_maintenance), mode, Icons.Outlined.Tune)
            state.operationBlockReason()?.let { Text(stringResource(it.textId())) }
            ListItem(content = { Text(stringResource(R.string.dashboard_check_only)) }, supportingContent = { Text(stringResource(R.string.dashboard_target_note)) }, leadingContent = { Icon(Icons.AutoMirrored.Outlined.FactCheck, null) }, modifier = Modifier.clickable(onClick = actions.onCheckInputs))
            ListItem(content = { Text(stringResource(R.string.dashboard_backup)) }, leadingContent = { Icon(Icons.Outlined.Backup, null) }, modifier = Modifier.clickable { onPanel(HomePanel.Backups) })
            ListItem(content = { Text(stringResource(R.string.dashboard_production_loader)) }, supportingContent = { Text(stringResource(R.string.home_sheet_production_note)) }, leadingContent = { Icon(Icons.Outlined.CheckCircle, null) })
            ListItem(content = { Text(stringResource(R.string.dashboard_loader)) }, supportingContent = { Text(stringResource(R.string.dashboard_experimental_gate)) }, leadingContent = { Icon(Icons.Outlined.Science, null) }, modifier = Modifier.clickable(onClick = actions.onOpenLoader))
        }
        else -> Unit
    }
}
