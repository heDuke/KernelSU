package me.weishu.kernelsu.ui.screen.install

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedCheckboxItem
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.util.LkmSelection

/**
 * @author weishu
 * @date 2024/3/12.
 */
@Composable
internal fun InstallScreenMaterial(
    uiState: InstallUiState,
    actions: InstallScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopBar(
                onBack = actions.onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.safeDrawingPadding()) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            InstallHeroCard(state = uiState)

            SelectInstallMethod(
                state = uiState,
                onSelected = actions.onSelectMethod,
                onDownloadFile = actions.onDownloadFile,
                onSelectBootImage = actions.onSelectBootImage,
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = buildList {
                    val isDownload = uiState.installMethod is InstallMethod.DownloadFile
                    val partitionItems = if (isDownload) {
                        uiState.remoteDisplayPartitions
                    } else {
                        uiState.displayPartitions
                    }
                    val partitionIndex = if (isDownload) {
                        uiState.remotePartitionSelectionIndex
                    } else {
                        uiState.partitionSelectionIndex
                    }
                    if (partitionItems.isNotEmpty()) add {
                        SegmentedDropdownItem(
                            enabled = uiState.canSelectPartition,
                            items = partitionItems,
                            selectedIndex = partitionIndex,
                            title = if (isDownload) {
                                stringResource(R.string.install_select_partition)
                            } else {
                                "${stringResource(R.string.install_select_partition)} (${uiState.slotSuffix})"
                            },
                            onItemSelected = actions.onSelectPartition,
                            icon = Icons.Filled.Edit
                        )
                    }
                    if (uiState.canForceBackup) add {
                        SegmentedCheckboxItem(
                            title = stringResource(R.string.install_force_backup),
                            summary = stringResource(R.string.install_force_backup_summary),
                            onCheckedChange = actions.onSelectForceBackup,
                            checked = uiState.forceBackup,
                        )
                    }
                    add {
                        SegmentedListItem(
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    null
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.install_upload_lkm_file)) },
                            supportingContent = {
                                (uiState.lkmSelection as? LkmSelection.LkmUri)?.let {
                                    Text(
                                        stringResource(
                                            R.string.selected_lkm,
                                            it.uri.lastPathSegment ?: "(file)"
                                        )
                                    )
                                }
                            },
                            trailingContent = {
                                if (uiState.lkmSelection is LkmSelection.LkmUri) {
                                    IconButton(onClick = actions.onClearLkm) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(android.R.string.cancel)
                                        )
                                    }
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            },
                            onClick = actions.onUploadLkm
                        )
                    }
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                item {
                    val rotationState by animateFloatAsState(
                        targetValue = if (uiState.advancedOptionsShown) 180f else 0f,
                        label = "RotationAnimation"
                    )
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.advanced_options)) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = stringResource(R.string.expand),
                                modifier = Modifier.graphicsLayer { rotationZ = rotationState }
                            )
                        },
                        onClick = actions.onAdvancedOptionsClicked
                    )
                }
                item(visible = uiState.advancedOptionsShown && uiState.showAdvancedSelectFile) {
                    SegmentedRadioItem(
                        title = stringResource(R.string.select_file),
                        summary = uiState.selectFileSummary,
                        selected = uiState.installMethod is InstallMethod.SelectFile,
                        onClick = actions.onSelectBootImage,
                    )
                }
                item(visible = uiState.advancedOptionsShown) {
                    SegmentedCheckboxItem(
                        title = stringResource(id = R.string.allow_shell),
                        summary = stringResource(id = R.string.allow_shell_summary),
                        checked = uiState.allowShell,
                        onCheckedChange = actions.onSelectAllowShell,
                    )
                }
                item(visible = uiState.advancedOptionsShown) {
                    SegmentedCheckboxItem(
                        title = stringResource(id = R.string.enable_adb),
                        summary = stringResource(id = R.string.enable_adb_summary),
                        checked = uiState.enableAdb,
                        onCheckedChange = actions.onSelectEnableAdb,
                    )
                }
            }
            ExpressivePrimaryBar(
                label = stringResource(R.string.install_next),
                onClick = actions.onNext,
                enabled = uiState.installMethod != null,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(
                Modifier.height(
                    16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun InstallHeroCard(state: InstallUiState) {
    val title: String
    val summary: String
    val icon: ImageVector
    val containerColor: Color
    when (state.installMethod) {
        is InstallMethod.DirectInstall -> {
            title = stringResource(R.string.direct_install)
            summary = stringResource(R.string.install_hero_direct_summary)
            icon = Icons.Outlined.CheckCircle
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        }
        is InstallMethod.DirectInstallToInactiveSlot -> {
            title = stringResource(R.string.install_inactive_slot)
            summary = stringResource(R.string.install_hero_inactive_summary)
            icon = Icons.Outlined.Warning
            containerColor = MaterialTheme.colorScheme.errorContainer
        }
        is InstallMethod.SelectFile -> {
            title = stringResource(R.string.select_file)
            summary = stringResource(R.string.install_hero_file_summary)
            icon = Icons.Outlined.InstallMobile
            containerColor = MaterialTheme.colorScheme.primaryContainer
        }
        is InstallMethod.DownloadFile -> {
            title = stringResource(R.string.download_file)
            summary = stringResource(R.string.install_hero_download_summary)
            icon = Icons.Outlined.Download
            containerColor = MaterialTheme.colorScheme.primaryContainer
        }
        null -> {
            title = stringResource(R.string.install_hero_choose)
            summary = stringResource(R.string.install_hero_choose_summary)
            icon = Icons.Outlined.Info
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        }
    }
    ExpressiveHeroCard(
        title = title,
        summary = summary,
        icon = icon,
        containerColor = containerColor,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SelectInstallMethod(
    state: InstallUiState,
    onSelected: (InstallMethod) -> Unit,
    onDownloadFile: () -> Unit,
    onSelectBootImage: () -> Unit,
) {
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        },
        onDismiss = null
    )
    val dialogTitle = stringResource(android.R.string.dialog_alert_title)
    val dialogContent = stringResource(R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.SelectFile -> onSelectBootImage()
            is InstallMethod.DownloadFile -> onDownloadFile()
            is InstallMethod.DirectInstall -> onSelected(option)
            is InstallMethod.DirectInstallToInactiveSlot -> confirmDialog.showConfirm(dialogTitle, dialogContent)
        }
    }

    key(state.installMethodOptions.size) {
        SegmentedColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            content = state.installMethodOptions.map { option ->
                {
                    SegmentedRadioItem(
                        title = stringResource(option.label),
                        summary = option.summary,
                        selected = option.javaClass == state.installMethod?.javaClass,
                        onClick = { onClick(option) }
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.install)) },
        navigationIcon = {
            TopBarBackButton(onClick = onBack)
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
