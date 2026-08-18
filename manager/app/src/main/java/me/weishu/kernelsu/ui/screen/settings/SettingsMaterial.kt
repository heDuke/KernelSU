package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SendLogBottomSheet
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.uninstalldialog.UninstallDialog

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val ksuValid = Natives.isManager

    UninstallDialog(
        show = showUninstallDialog.value,
        onDismissRequest = { showUninstallDialog.value = false }
    )

    ExpressiveScaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.padding(bottom = bottomInnerPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            val themeModeItems = listOf(
                stringResource(id = R.string.settings_theme_mode_system),
                stringResource(id = R.string.settings_theme_mode_light),
                stringResource(id = R.string.settings_theme_mode_dark),
            )
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(id = R.string.settings_appearance),
                content = listOf(
                    {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Palette,
                            title = stringResource(id = R.string.settings_theme),
                            summary = stringResource(id = R.string.settings_theme_summary),
                            items = themeModeItems,
                            selectedIndex = uiState.themeMode.coerceIn(0, themeModeItems.lastIndex),
                            onItemSelected = actions.onSetThemeMode
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(id = R.string.settings_dynamic_color),
                            summary = stringResource(id = R.string.settings_dynamic_color_summary),
                            checked = uiState.dynamicColor,
                            onCheckedChange = actions.onSetDynamicColor
                        )
                    }
                )
            )

            KsuIsValid {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    title = stringResource(id = R.string.settings_section_updates),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Update,
                                title = stringResource(id = R.string.settings_check_update),
                                summary = stringResource(id = R.string.settings_check_update_summary),
                                checked = uiState.checkUpdate,
                                onCheckedChange = actions.onSetCheckUpdate
                            )
                        },
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(id = R.string.settings_module_check_update),
                                summary = stringResource(id = R.string.settings_check_update_summary),
                                checked = uiState.checkModuleUpdate,
                                onCheckedChange = actions.onSetCheckModuleUpdate
                            )
                        }
                    )
                )
            }

            val profileTemplate = stringResource(id = R.string.settings_profile_template)
            val moduleRepo = stringResource(id = R.string.settings_module_repo)
            val envCheck = stringResource(id = R.string.env_check_title)
            val about = stringResource(id = R.string.settings_about)
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                item(visible = ksuValid) {
                    SegmentedListItem(
                        onClick = actions.onOpenProfileTemplate,
                        headlineContent = { Text(profileTemplate) },
                        supportingContent = { Text(stringResource(id = R.string.settings_profile_template_summary)) },
                        leadingContent = { Icon(Icons.Filled.Fence, profileTemplate) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null
                            )
                        }
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = actions.onOpenModuleRepo,
                        headlineContent = { Text(moduleRepo) },
                        supportingContent = { Text(stringResource(id = R.string.settings_module_repo_summary)) },
                        leadingContent = { Icon(Icons.Filled.Extension, moduleRepo) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null
                            )
                        }
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = actions.onOpenEnvCheck,
                        headlineContent = { Text(envCheck) },
                        supportingContent = { Text(stringResource(id = R.string.env_check_summary)) },
                        leadingContent = { Icon(Icons.Filled.HealthAndSafety, envCheck) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null
                            )
                        }
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = actions.onOpenAbout,
                        headlineContent = { Text(about) },
                        supportingContent = {
                            Text(stringResource(id = R.string.settings_about_summary))
                        },
                        leadingContent = {
                            Icon(Icons.Filled.ContactPage, about)
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null
                            )
                        }
                    )
                }
            }

            if (uiState.isLkmMode) {
                val uninstall = stringResource(id = R.string.settings_uninstall)
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    title = stringResource(id = R.string.settings_section_danger),
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = { showUninstallDialog.value = true },
                                enabled = !uiState.isLateLoadMode,
                                headlineContent = { Text(uninstall) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = uninstall,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    )
                )
            }

            val suCompatModeItems = listOf(
                stringResource(id = R.string.settings_mode_enable_by_default),
                stringResource(id = R.string.settings_mode_disable_until_reboot),
                stringResource(id = R.string.settings_mode_disable_always),
            )
            val advancedExpanded = showAdvanced
            val rotationState by animateFloatAsState(
                targetValue = if (advancedExpanded) 180f else 0f,
                label = "AdvancedRotation"
            )
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(id = R.string.settings_section_advanced),
            ) {
                item {
                    SegmentedListItem(
                        onClick = { showAdvanced = !showAdvanced },
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (advancedExpanded) R.string.settings_advanced_hide
                                    else R.string.settings_advanced_show
                                )
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = rotationState }
                            )
                        }
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    val suSummary = when (uiState.suCompatStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sucompat_summary)
                    }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.RemoveModerator,
                        title = stringResource(id = R.string.settings_sucompat),
                        summary = suSummary,
                        items = suCompatModeItems,
                        enabled = uiState.suCompatStatus == "supported",
                        selectedIndex = uiState.suCompatMode,
                        onItemSelected = actions.onSetSuCompatMode
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    val umountSummary = when (uiState.kernelUmountStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_kernel_umount_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.RemoveCircle,
                        title = stringResource(id = R.string.settings_kernel_umount),
                        summary = umountSummary,
                        enabled = uiState.kernelUmountStatus == "supported",
                        checked = uiState.isKernelUmountEnabled,
                        onCheckedChange = actions.onSetKernelUmountEnabled
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_selinux_hide_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Policy,
                        title = stringResource(id = R.string.settings_selinux_hide),
                        summary = selinuxHideSummary,
                        enabled = uiState.selinuxHideStatus == "supported",
                        checked = uiState.isSelinuxHideEnabled,
                        onCheckedChange = actions.onSetSelinuxHideEnabled
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    val sulogSummary = when (uiState.sulogStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sulog_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(id = R.string.settings_sulog),
                        summary = sulogSummary,
                        enabled = uiState.sulogStatus == "supported",
                        checked = uiState.isSulogEnabled,
                        onCheckedChange = actions.onSetSulogEnabled
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    val adbRootSummary = when (uiState.adbRootStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_adb_root_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Adb,
                        title = stringResource(id = R.string.settings_adb_root),
                        summary = adbRootSummary,
                        enabled = uiState.adbRootStatus == "supported",
                        checked = uiState.isAdbRootEnabled,
                        onCheckedChange = actions.onSetAdbRootEnabled
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.RestartAlt,
                        title = stringResource(id = R.string.settings_soft_reboot),
                        summary = stringResource(id = R.string.settings_soft_reboot_summary),
                        enabled = !uiState.isLateLoadMode,
                        checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                        onCheckedChange = actions.onSetUseSoftReboot
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.FolderDelete,
                        title = stringResource(id = R.string.settings_umount_modules_default),
                        summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                        checked = uiState.isDefaultUmountModules,
                        onCheckedChange = actions.onSetDefaultUmountModules
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.DeveloperMode,
                        title = stringResource(id = R.string.enable_web_debugging),
                        summary = stringResource(id = R.string.enable_web_debugging_summary),
                        checked = uiState.enableWebDebugging,
                        onCheckedChange = actions.onSetEnableWebDebugging
                    )
                }
                item(visible = advancedExpanded && ksuValid) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.ElectricalServices,
                        title = stringResource(id = R.string.settings_auto_jailbreak),
                        summary = stringResource(id = R.string.settings_auto_jailbreak_summary),
                        enabled = uiState.isLateLoadMode,
                        checked = uiState.autoJailbreak,
                        onCheckedChange = actions.onSetAutoJailbreak
                    )
                }
                item(visible = advancedExpanded) {
                    SegmentedListItem(
                        onClick = { showBottomSheet = true },
                        headlineContent = { Text(stringResource(id = R.string.send_log)) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.BugReport,
                                stringResource(id = R.string.send_log)
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showBottomSheet) {
                SendLogBottomSheet(
                    onDismiss = { showBottomSheet = false },
                    snackbarHostState = snackBarHost,
                )
            }
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
