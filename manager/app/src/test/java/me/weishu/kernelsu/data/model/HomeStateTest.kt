package me.weishu.kernelsu.data.model

import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.ui.screen.home.*
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import org.junit.Assert.*
import org.junit.Test

class HomeStateTest {
    private fun healthy() = HomeUiState(KernelVersion(6, 1, 172), 32631, 2, 2, true,
        true, false, false, false, false, true, false, false, false,
        LatestVersionInfo(), 32642, SystemInfo("kernel", "manager", "device", "fingerprint", "Enforcing", 2), loaded = true)

    @Test fun authorizationAndProbingAreNotProofOfMissingDriver() {
        assertEquals(HomeActivation.Unknown, healthy().copy(isManager = false, ksuVersion = null).activation)
        val probing = healthy().copy(loaded = false, isRootAvailable = false)
        assertEquals(HomeActivation.Checking, probing.activation)
        assertFalse(probing.showRootWarning)
        assertEquals(HomeBlockReason.Checking, probing.operationBlockReason())
    }
    @Test fun operationPrerequisitesAreSpecificAndSafeModeIsContextual() {
        assertNull(healthy().operationBlockReason())
        assertEquals(HomeBlockReason.Authorization, healthy().copy(isManager = false).operationBlockReason())
        assertEquals(HomeBlockReason.Interface, healthy().copy(requiresNewKernel = true, isRootAvailable = false).operationBlockReason())
        assertEquals(HomeBlockReason.Root, healthy().copy(isRootAvailable = false).operationBlockReason())
        assertEquals(HomeBlockReason.SafeMode, healthy().copy(isSafeMode = true).operationBlockReason())
        assertNull(healthy().copy(isSafeMode = true).operationBlockReason(allowSafeMode = true))
        assertEquals(HomeBlockReason.Busy, healthy().copy(moduleBatchBusy = true).operationBlockReason())
    }
    @Test fun buildCountersDoNotBecomeInterfaceConflicts() {
        assertFalse(healthy().showVersionMismatchWarning)
        assertEquals(HomeActivation.Active, healthy().activation)
    }
}
