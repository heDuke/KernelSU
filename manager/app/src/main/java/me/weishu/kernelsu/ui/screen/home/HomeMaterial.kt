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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveNoticeCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSectionTitle
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
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.husky_update_lkm),
                            onClick = {
                                flashDialog.showConfirm(
                                    title = confirmTitle,
                                    content = confirmText,
                                    confirm = confirmTitle,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.huskyBusy,
                            tonal = false,
                            icon = Icons.Outlined.SystemUpdateAlt,
                        )
                    } else {
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.husky_check_update),
                            onClick = actions.onCheckHuskyUpdate,
                            modifier = Modifier.weight(1f),
                            enabled = !state.huskyBusy,
                            tonal = true,
                            icon = Icons.Outlined.SystemUpdateAlt,
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
                ExpressivePrimaryBar(
                    label = stringResource(R.string.husky_open_release),
                    onClick = actions.onOpenHuskyRelease,
                    enabled = !state.huskyBusy,
                    tonal = true,
                    icon = Icons.Outlined.OpenInNew,
                )
                ExpressivePrimaryBar(
                    label = stringResource(R.string.husky_download_lkm),
                    onClick = actions.onDownloadLkmToDownloads,
                    enabled = !state.huskyBusy,
                    tonal = false,
                    icon = Icons.Outlined.Download,
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
                ExpressivePrimaryBar(
                    label = otaAction,
                    onClick = {
                        otaDialog.showConfirm(
                            title = otaTitle,
                            content = otaConfirmText,
                            confirm = otaAction,
                        )
                    },
                    enabled = !state.huskyBusy,
                    tonal = true,
                    icon = Icons.Outlined.SystemUpdateAlt,
                )
            }
        }
    }
}

private fun huskyReleaseSummary(body: String?): String? {
    val line = body?.lineSequence()
        ?.map { stripReleaseMarkdown(it) }
        ?.firstOrNull { it.isNotEmpty() }
        ?: return null
    return if (line.length <= 160) line else line.take(157).trimEnd() + "..."
}

private fun stripReleaseMarkdown(raw: String): String {
    var line = raw.trim()
    line = when {
        line.startsWith("- ") -> line.removePrefix("- ")
        line.startsWith("* ") -> line.removePrefix("* ")
        line.startsWith("#") -> line.trimStart('#').trimStart()
        else -> line
    }
    return line.replace("**", "").replace("`", "").trim()
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
    val versionMismatch = state.showVersionMismatchWarning

    val containerColor = when {
        versionMismatch -> MaterialTheme.colorScheme.tertiaryContainer
        ksuActive -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    val statusIcon = when {
        versionMismatch -> Icons.Outlined.Warning
        ksuActive -> Icons.Outlined.CheckCircle
        notInstalled -> Icons.Outlined.Warning
        else -> Icons.Outlined.Block
    }
    val statusTitle = when {
        versionMismatch -> stringResource(
            R.string.home_version_mismatch,
            state.currentManagerVersionCode,
            state.ksuVersion ?: 0,
        )
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val statusSummary = when {
        versionMismatch -> stringResource(
            R.string.home_working_version,
            "${state.ksuVersion}-${state.kernelUAPIVersion}",
        )
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

    val hasTags = ksuActive && (
        workingMode.isNotEmpty() || state.isSafeMode || state.isLateLoadMode
    )

    ExpressiveHeroCard(
        title = statusTitle,
        summary = statusSummary,
        icon = statusIcon,
        containerColor = containerColor,
        onClick = if (!state.isLateLoadMode) {
            { actions.onInstallClick() }
        } else {
            null
        },
        tags = if (hasTags) {
            {
                FlowRow(
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
        } else {
            null
        },
    )
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
    val icon = when (level) {
        WarningLevel.Error -> Icons.Outlined.Warning
        WarningLevel.Notice -> Icons.Outlined.Info
    }
    ExpressiveNoticeCard(
        message = message,
        containerColor = containerColor,
        icon = icon,
        onClick = onClick,
    )
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

    Column {
        ExpressiveSectionTitle(title = stringResource(R.string.home_device_section))
        TonalCard(
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(
                    label = stringResource(R.string.home_manager_version),
                    value = systemInfo.managerVersion,
                )
                InfoRow(
                    label = stringResource(R.string.home_kernel),
                    value = systemInfo.kernelVersion,
                )
                InfoRow(
                    label = stringResource(R.string.home_device_model),
                    value = systemInfo.deviceModel,
                )
                InfoRow(
                    label = stringResource(R.string.home_fingerprint),
                    value = systemInfo.fingerprint,
                )
                InfoRow(
                    label = stringResource(R.string.home_selinux_status),
                    value = selinuxDisplay,
                )
                InfoRow(
                    label = stringResource(R.string.home_seccomp_status),
                    value = seccompDisplay,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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

@Preview(name = "Version mismatch")
@Composable
private fun StatusCardMismatchPreview() {
    StatusCard(
        state = previewHomeScreenState(
            ksuVersion = 12345,
            lkmMode = true,
            currentManagerVersionCode = 10000,
        ),
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
    currentManagerVersionCode: Long = ksuVersion?.toLong() ?: 10000,
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
    currentManagerVersionCode = currentManagerVersionCode,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
    uapiMismatch = false
)
