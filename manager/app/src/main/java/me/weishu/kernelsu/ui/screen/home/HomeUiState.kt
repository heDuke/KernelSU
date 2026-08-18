package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.data.model.HuskyRelease
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

enum class HuskyUpdateStatus {
    Idle,
    Checking,
    Available,
    UpToDate,
    Error,
    Downloading,
}

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
    val isAbDevice: Boolean = false,
    val envOverall: EnvCheckSeverity? = null,
    val envSummaryLine: String? = null,
) {
    val isFullFeatured: Boolean
        get() = isManager && !requiresNewKernel && isRootAvailable

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    val showRequireKernelWarning: Boolean
        get() = isManager && requiresNewKernel

    val showUAPIMisMatchWarning: Boolean
        get() = isManager && showRequireKernelWarning && uapiMismatch

    val showRootWarning: Boolean
        get() = ksuVersion != null && !isRootAvailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val showVersionMismatchWarning: Boolean
        get() = ksuVersion != null && ksuVersion.toLong() != currentManagerVersionCode

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
    val onAlignEnvironment: () -> Unit = {},
)
