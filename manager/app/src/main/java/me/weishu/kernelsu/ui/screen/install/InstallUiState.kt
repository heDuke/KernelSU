package me.weishu.kernelsu.ui.screen.install

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.ui.util.LkmSelection

@Immutable
internal data class InstallUiState(
    val installMethod: InstallMethod?,
    val lkmSelection: LkmSelection,
    val partitionSelectionIndex: Int,
    val displayPartitions: List<String>,
    val remotePartitionSelectionIndex: Int,
    val remoteDisplayPartitions: List<String>,
    val currentKmi: String,
    val slotSuffix: String,
    val installMethodOptions: List<InstallMethod>,
    val canSelectPartition: Boolean,
    val advancedOptionsShown: Boolean,
    val allowShell: Boolean,
    val enableAdb: Boolean,
    val forceBackup: Boolean,
    val canForceBackup: Boolean,
    val showAdvancedSelectFile: Boolean,
    val selectFileSummary: String?,
    val flashLocalImage: Boolean = false,
    val canFlashLocalImage: Boolean = false,
    val checkOnly: Boolean = false,
    val reviewing: Boolean = false,
    val outputSummary: String? = null,
    val blockModulesEnabled: Boolean = false,
    val blockModules: String = "",
)

@Immutable
internal data class InstallScreenActions(
    val onBack: () -> Unit,
    val onCheckOnly: () -> Unit,
    val onSelectLocalFlash: (Boolean) -> Unit,
    val onPickOutput: () -> Unit,
    val onClearOutput: () -> Unit,
    val onSelectMethod: (InstallMethod) -> Unit,
    val onDownloadFile: () -> Unit,
    val onSelectBootImage: () -> Unit,
    val onUploadLkm: () -> Unit,
    val onClearLkm: () -> Unit,
    val onSelectPartition: (Int) -> Unit,
    val onNext: () -> Unit,
    val onAdvancedOptionsClicked: () -> Unit,
    val onSelectAllowShell: (Boolean) -> Unit,
    val onSelectEnableAdb: (Boolean) -> Unit,
    val onSelectForceBackup: (Boolean) -> Unit,
    val onSelectBlockModules: (Boolean) -> Unit,
    val onBlockModulesChanged: (String) -> Unit,
)
