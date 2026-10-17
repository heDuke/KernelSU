package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.data.model.HuskyRelease
import me.weishu.kernelsu.data.model.DriverVersionStatus
import me.weishu.kernelsu.data.model.driverVersionStatus
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

enum class HuskyUpdateStatus {
    Idle,
    Checking,
    Available,
    UpToDate,
    Error,
    MetadataError,
    DownloadError,
    NoMatchingLkm,
    Downloading,
}

enum class HomeBlockReason { Checking, Authorization, Interface, Root, SafeMode, Busy }
enum class HomeActivation { Checking, Unknown, Attention, Active }

@Immutable
data class HomeUiState(
    val kernelVersion: KernelVersion,
    val ksuVersion: Int?,
    val managerUAPIVersion: Int,
    val kernelUAPIVersion: Int?,
    val lkmMode: Boolean?,
    val isManager: Boolean,
    val isManagerPrBuild: Boolean,
    val isKernelPrBuild: Boolean,
    val requiresNewKernel: Boolean,
    val uapiMismatch: Boolean,
    val isRootAvailable: Boolean,
    val isSafeMode: Boolean,
    val isLateLoadMode: Boolean,
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentManagerVersionCode: Long,
    val systemInfo: SystemInfo,
    val huskyRelease: HuskyRelease? = null,
    val huskyUpdateStatus: HuskyUpdateStatus = HuskyUpdateStatus.Idle,
    val huskyError: String? = null,
    val huskyDiagnostic: String? = null,
    val isAbDevice: Boolean = false,
    val moduleBatchBusy: Boolean = false,
    val loaded: Boolean = false,
    val observedAt: Long = 0,
    val observedWallTime: Long = 0,
    val runtime: me.weishu.kernelsu.data.model.RuntimeRecord = me.weishu.kernelsu.data.model.RuntimeRecord(),
    val runtimeError: String? = null,
    val pageSize: Int? = null,
    val slot: String? = null,
    val envReport: me.weishu.kernelsu.data.model.EnvCheckReport? = null,
    val envOverall: EnvCheckSeverity? = null,
    val envSummaryLine: String? = null,
    val envChecking: Boolean = false,
    val pendingReboot: Boolean = false,
    val pendingRebootError: String? = null,
) {
    val activation: HomeActivation
        get() = when {
            !loaded -> HomeActivation.Checking
            !isManager || ksuVersion == null -> HomeActivation.Unknown
            !isRootAvailable || requiresNewKernel || isSafeMode -> HomeActivation.Attention
            else -> HomeActivation.Active
        }

    fun operationBlockReason(allowSafeMode: Boolean = false): HomeBlockReason? = when {
        !loaded -> HomeBlockReason.Checking
        !isManager -> HomeBlockReason.Authorization
        requiresNewKernel || showVersionMismatchWarning -> HomeBlockReason.Interface
        !isRootAvailable -> HomeBlockReason.Root
        isSafeMode && !allowSafeMode -> HomeBlockReason.SafeMode
        moduleBatchBusy -> HomeBlockReason.Busy
        else -> null
    }
    val isFullFeatured: Boolean
        get() = isManager && !requiresNewKernel && isRootAvailable

    val showRequireKernelWarning: Boolean
        get() = isManager && requiresNewKernel

    val showUAPIMisMatchWarning: Boolean
        get() = isManager && showRequireKernelWarning && uapiMismatch

    val showRootWarning: Boolean
        get() = loaded && ksuVersion != null && !isRootAvailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val showVersionMismatchWarning: Boolean
        get() = driverVersionStatus(currentManagerVersionCode, ksuVersion, managerUAPIVersion,
            kernelUAPIVersion, requiresNewKernel) == DriverVersionStatus.Incompatible

    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode

    val canDirectInstallLkm: Boolean
        get() = isRootAvailable && ksuVersion != null

    val huskyBusy: Boolean
        get() = huskyUpdateStatus == HuskyUpdateStatus.Checking ||
            huskyUpdateStatus == HuskyUpdateStatus.Downloading

    val showOtaSlotCard: Boolean
        get() = isAbDevice && isFullFeatured

    val envAlignAvailable: Boolean
        get() = huskyUpdateStatus == HuskyUpdateStatus.Available && canDirectInstallLkm
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onCheckHuskyUpdate: () -> Unit = {},
    val onUpdateLkm: () -> Unit = {},
    val onDownloadLkmToDownloads: () -> Unit = {},
    val onOpenHuskyRelease: () -> Unit = {},
    val onInstallInactiveSlot: () -> Unit = {},
    val onOpenEnvCheck: () -> Unit = {},
    val onCheckInputs: () -> Unit = {},
    val onInstallModule: () -> Unit = {},
    val onOpenModules: () -> Unit = {},
    val onOpenLoader: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onExportReport: () -> Unit = {},
    val onReboot: () -> Unit = {},
    val onApplyModuleBatch: (ModuleBatchReview) -> Unit = {},
    val onDisableModules: () -> Unit = {},
    val onRestoreModules: () -> Unit = {},
    val onAlignEnvironment: () -> Unit = {},
)
