package me.weishu.kernelsu.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.component.statustag.StatusTag

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            StatusCard(
                state = state,
                actions = actions,
            )
            HuskyUpdateCard(state = state, actions = actions)
            if (state.checkUpdateEnabled) {
                UpdateCard(state = state, actions = actions)
            }
            if (state.showManagerPrBuildWarning) {
                WarningCard(stringResource(id = R.string.home_pr_build_warning), level = WarningLevel.Notice)
            } else if (state.showKernelPrBuildWarning) {
                WarningCard(stringResource(id = R.string.home_pr_kernel_warning), level = WarningLevel.Notice)
            }
            if (state.showVersionMismatchWarning) {
                WarningCard(
                    stringResource(
                        id = R.string.home_version_mismatch,
                        state.currentManagerVersionCode,
                        state.ksuVersion ?: 0
                    )
                )
            }
            if (state.showGkiWarning) {
                WarningCard(stringResource(id = R.string.home_gki_warning), level = WarningLevel.Notice)
            }
            if (state.showUAPIMisMatchWarning) {
                WarningCard(
                    stringResource(
                        id = R.string.uapi_mismatch,
                        state.managerUAPIVersion,
                        state.kernelUAPIVersion ?: 0,
                    )
                )
            }
            if (state.showRequireKernelWarning) {
                if (state.currentManagerVersionCode < (state.ksuVersion ?: 0)) {
                    WarningCard(
                        stringResource(
                            id = R.string.require_manager_version,
                            state.currentManagerVersionCode,
                            state.ksuVersion ?: 0,
                        )
                    )
                } else {
                    WarningCard(
                        stringResource(
                            id = R.string.require_kernel_version,
                            state.ksuVersion ?: 0,
                            Natives.MINIMAL_SUPPORTED_KERNEL
                        )
                    )
                }
            }
            if (state.showRootWarning) {
                WarningCard(stringResource(id = R.string.grant_root_failed))
            }
            InfoCard(systemInfo = state.systemInfo)
            LearnMoreCard(onOpenUrl = actions.onOpenUrl)
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun HuskyUpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val release = state.huskyRelease
    val flashDialog = rememberConfirmDialog(onConfirm = actions.onUpdateLkm)
    val otaDialog = rememberConfirmDialog(onConfirm = actions.onInstallInactiveSlot)
    val confirmTitle = stringResource(R.string.husky_update_lkm)
    val confirmText = stringResource(R.string.husky_update_confirm, release?.tag ?: "")
    val available = state.huskyUpdateStatus == HuskyUpdateStatus.Available &&
        !release?.lkmDownloadUrl.isNullOrEmpty()

    val lkmTitle: String
    val lkmSummary: String
    when {
        !state.canDirectInstallLkm -> {
            lkmTitle = stringResource(R.string.husky_not_installed_title)
            lkmSummary = stringResource(R.string.husky_not_installed_guidance)
        }
        state.huskyUpdateStatus == HuskyUpdateStatus.Error -> {
            lkmTitle = stringResource(R.string.husky_update_card_title)
            lkmSummary = state.huskyError ?: stringResource(R.string.husky_update_card_summary)
        }
        state.huskyUpdateStatus == HuskyUpdateStatus.UpToDate && release != null -> {
            lkmTitle = stringResource(R.string.husky_update_card_title)
            lkmSummary = stringResource(R.string.husky_update_up_to_date, release.tag)
        }
        available -> {
            lkmTitle = stringResource(R.string.husky_update_available, release?.tag ?: "")
            lkmSummary = huskyReleaseSummary(release?.body)
                ?: stringResource(R.string.husky_update_card_summary)
        }
        else -> {
            lkmTitle = stringResource(R.string.husky_update_card_title)
            lkmSummary = stringResource(R.string.husky_update_card_summary)
        }
    }

    val containerColor = if (available && state.canDirectInstallLkm) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

    val otaTitle = stringResource(R.string.husky_ota_slot_title)
    val otaSummary = stringResource(R.string.husky_ota_slot_summary)
    val otaConfirmText = stringResource(R.string.husky_ota_slot_confirm)
    val otaAction = stringResource(R.string.husky_ota_slot_action)

    TonalCard(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.husky_update_section_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = lkmTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = lkmSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.canDirectInstallLkm) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.huskyBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    if (available) {
                        HuskyWideButton(
                            onClick = {
                                flashDialog.showConfirm(
                                    title = confirmTitle,
                                    content = confirmText,
                                    confirm = confirmTitle,
                                )
                            },
                            enabled = !state.huskyBusy,
                            tonal = false,
                            icon = Icons.Outlined.SystemUpdateAlt,
                            label = stringResource(R.string.husky_update_lkm),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        HuskyWideButton(
                            onClick = actions.onCheckHuskyUpdate,
                            enabled = !state.huskyBusy,
                            tonal = true,
                            icon = Icons.Outlined.SystemUpdateAlt,
                            label = stringResource(R.string.husky_check_update),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                if (state.huskyBusy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                HuskyWideButton(
                    onClick = actions.onOpenHuskyRelease,
                    enabled = !state.huskyBusy,
                    tonal = true,
                    icon = Icons.Outlined.OpenInNew,
                    label = stringResource(R.string.husky_open_release),
                    modifier = Modifier.fillMaxWidth(),
                )
                HuskyWideButton(
                    onClick = actions.onDownloadLkmToDownloads,
                    enabled = !state.huskyBusy,
                    tonal = false,
                    icon = Icons.Outlined.Download,
                    label = stringResource(R.string.husky_download_lkm),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.showOtaSlotCard) {
                HorizontalDivider()
                Text(
                    text = otaTitle,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = otaSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HuskyWideButton(
                    onClick = {
                        otaDialog.showConfirm(
                            title = otaTitle,
                            content = otaConfirmText,
                            confirm = otaAction,
                        )
                    },
                    enabled = true,
                    tonal = true,
                    icon = Icons.Outlined.SystemUpdateAlt,
                    label = otaAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HuskyWideButton(
    onClick: () -> Unit,
    enabled: Boolean,
    tonal: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier.height(56.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            content = content,
        )
    }
}

private fun huskyReleaseSummary(body: String?): String? {
    val line = body?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("```") }
        ?: return null
    return if (line.length <= 160) line else line.take(157).trimEnd() + "..."
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            level = WarningLevel.Notice
        ) {
            if (newVersion.changelog.isEmpty()) {
                actions.onOpenUrl(newVersion.downloadUrl)
            } else {
                updateDialog.showConfirm(
                    title = title,
                    content = newVersion.changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = { RebootListPopup() },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val containerColor = if (ksuActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val statusIcon = when {
        ksuActive -> Icons.Outlined.CheckCircle
        notInstalled -> Icons.Outlined.Warning
        else -> Icons.Outlined.Block
    }
    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val statusSummary = when {
        ksuActive -> stringResource(R.string.home_working_version, "${state.ksuVersion}-${state.kernelUAPIVersion}")
        notInstalled -> stringResource(R.string.home_click_to_install)
        else -> stringResource(R.string.home_unsupported_reason)
    }
    val workingMode = if (ksuActive) {
        when (state.lkmMode) {
            null -> ""
            true -> "LKM"
            else -> "GKI"
        }
    } else ""

    TonalCard(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = containerColor,
        onClick = {
            if (!state.isLateLoadMode) {
                actions.onInstallClick()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusTitle,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = statusTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            val hasTags = ksuActive && (
                workingMode.isNotEmpty() || state.isSafeMode || state.isLateLoadMode
            )
            if (hasTags) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (workingMode.isNotEmpty()) {
                        StatusTag(
                            label = workingMode,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.isSafeMode) {
                        StatusTag(
                            label = stringResource(id = R.string.safe_mode),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            backgroundColor = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                    if (state.isLateLoadMode) {
                        StatusTag(
                            label = stringResource(id = R.string.jailbreak_mode),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            backgroundColor = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusSummary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        TonalCard(
            containerColor = containerColor,
            shape = MaterialTheme.shapes.extraLarge,
            onClick = onClick,
            content = content,
        )
    } else {
        TonalCard(
            containerColor = containerColor,
            shape = MaterialTheme.shapes.extraLarge,
            content = content,
        )
    }
}

@Composable
private fun LearnMoreCard(onOpenUrl: (String) -> Unit) {
    val url = stringResource(R.string.home_learn_kernelsu_url)
    TonalCard(
        shape = MaterialTheme.shapes.extraLarge,
        onClick = { onOpenUrl(url) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(systemInfo: SystemInfo) {
    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    SegmentedColumn(
        title = stringResource(R.string.home_device_section),
        content = listOf(
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_manager_version)) },
                    supportingContent = { Text(systemInfo.managerVersion) },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_kernel)) },
                    supportingContent = { Text(systemInfo.kernelVersion) },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_device_model)) },
                    supportingContent = { Text(systemInfo.deviceModel) },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_fingerprint)) },
                    supportingContent = { Text(systemInfo.fingerprint) },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_selinux_status)) },
                    supportingContent = { Text(selinuxDisplay) },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_seccomp_status)) },
                    supportingContent = { Text(seccompDisplay) },
                )
            },
        ),
    )
}

@Preview(name = "Activated")
@Composable
private fun StatusCardActivatedPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Not Activated")
@Composable
private fun StatusCardNotActivatedPreview() {
    StatusCard(state = previewHomeScreenState(ksuVersion = null, lkmMode = null), actions = HomeActions({}, {}))
}

@Preview(name = "Permissive")
@Composable
private fun StatusCardPermissivePreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive"),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Jailbreak")
@Composable
private fun StatusCardJailbreakPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true),
        actions = HomeActions({}, {})
    )
}

private val previewSystemInfo = SystemInfo(
    kernelVersion = "6.1.0-android14-0-g123456789000-ab12345678",
    managerVersion = "3.0.0 (30000)",
    deviceModel = "Google Pixel 6 Pro",
    fingerprint = "google/raven/raven:14/AP1A.240305.019:user/release-keys",
    selinuxStatus = "Enforcing",
    seccompStatus = 2
)

private val previewUriHandler = object : UriHandler {
    override fun openUri(uri: String) {}
}

@Composable
private fun HomeScreenPreviewContent(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) {
    CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val actions = HomeActions({}, {})
            val state = previewHomeScreenState(
                ksuVersion = ksuVersion,
                lkmMode = lkmMode,
                isSafeMode = isSafeMode,
                isLateLoadMode = isLateLoadMode,
                selinuxStatus = selinuxStatus,
            )
            StatusCard(
                state = state,
                actions = actions
            )
            HuskyUpdateCard(state = state, actions = actions)
            InfoCard(previewSystemInfo.copy(selinuxStatus = selinuxStatus))
            LearnMoreCard(onOpenUrl = {})
        }
    }
}

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeScreenActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true)
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeScreenNotActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null)
}

@Preview(name = "Home Permissive", showBackground = true)
@Composable
private fun HomeScreenPermissivePreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive")
}

@Preview(name = "Home Jailbreak", showBackground = true)
@Composable
private fun HomeScreenJailbreakPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true)
}

private fun previewHomeScreenState(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) = HomeUiState(
    kernelVersion = KernelVersion(6, 1, 0),
    ksuVersion = ksuVersion,
    lkmMode = lkmMode,
    isManager = true,
    isManagerPrBuild = false,
    isKernelPrBuild = false,
    requiresNewKernel = false,
    isRootAvailable = ksuVersion != null,
    isSafeMode = isSafeMode,
    isLateLoadMode = isLateLoadMode,
    checkUpdateEnabled = false,
    latestVersionInfo = me.weishu.kernelsu.ui.util.module.LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
    uapiMismatch = false
)
