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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
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
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveNoticeCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import androidx.compose.material3.Scaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSectionTitle
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.component.statustag.StatusTag

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
        !release?.lkmAssets.isNullOrEmpty()

    val lkmTitle: String
    val lkmSummary: String
    when {
        !state.canDirectInstallLkm -> {
            lkmTitle = stringResource(R.string.husky_not_installed_title)
            lkmSummary = stringResource(R.string.husky_not_installed_guidance)
        }
        state.huskyUpdateStatus in listOf(HuskyUpdateStatus.Error, HuskyUpdateStatus.MetadataError, HuskyUpdateStatus.DownloadError, HuskyUpdateStatus.NoMatchingLkm) -> {
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
    val contentColor = contentColorFor(containerColor)

    val otaTitle = stringResource(R.string.husky_ota_slot_title)
    val otaSummary = stringResource(R.string.husky_ota_slot_summary)
    val otaConfirmText = stringResource(R.string.husky_ota_slot_confirm)
    val otaAction = stringResource(R.string.husky_ota_slot_action)

    TonalCard(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = containerColor,
        contentColor = contentColor,
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
                color = contentColor,
                maxLines = if (state.huskyUpdateStatus == HuskyUpdateStatus.NoMatchingLkm ||
                    state.huskyUpdateStatus == HuskyUpdateStatus.Error) Int.MAX_VALUE else 6,
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
                            .heightIn(min = 48.dp),
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
                    color = contentColor,
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
internal fun HomeReleaseActions(state: HomeUiState, actions: HomeActions) {
    HuskyUpdateCard(state, actions)
    if (state.checkUpdateEnabled) UpdateCard(state, actions)
}
