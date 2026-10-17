package me.weishu.kernelsu.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RootGateTest {
    private fun facts(
        manager: Boolean = true, safe: Boolean = false, root: Boolean = true,
        version: Int? = 32762, kernel: Int? = 6, managerUapi: Int = 6,
    ) = RootGateFacts(manager, safe, root, version, kernel, managerUapi)

    @Test fun matchedAllowsEverything() {
        for (op in RootOperation.entries) assertEquals(RootGateResult.Allowed, evaluateRootGate(op, facts()))
        assertEquals(InterfaceRelation.Matched, facts().relation)
    }

    @Test fun olderKernelAllowsBootPatchButBlocksModules() {
        val f = facts(version = 32643, kernel = 2)
        assertEquals(InterfaceRelation.KernelOutdated, f.relation)
        assertEquals(RootGateResult.Allowed, evaluateRootGate(RootOperation.BootPatch, f))
        assertEquals(RootGateResult.KernelOutdated(2, 6), evaluateRootGate(RootOperation.ModuleMutation, f))
    }

    @Test fun legacyKernelWithoutUapiCountsAsOutdated() {
        val f = facts(version = 32400, kernel = 0)
        assertEquals(RootGateResult.Allowed, evaluateRootGate(RootOperation.BootPatch, f))
        assertEquals(RootGateResult.KernelOutdated(0, 6), evaluateRootGate(RootOperation.ModuleMutation, f))
    }

    @Test fun belowMinimalVersionCountsAsOutdatedEvenIfUapiMatches() {
        assertEquals(InterfaceRelation.KernelOutdated, facts(version = 32000).relation)
        assertEquals(InterfaceRelation.Matched, facts(version = -1).relation)
    }

    @Test fun newerKernelBlocksEverything() {
        val f = facts(kernel = 7)
        for (op in RootOperation.entries) assertEquals(RootGateResult.ManagerOutdated(7, 6), evaluateRootGate(op, f))
    }

    @Test fun authorizationSafeModeAndRootTakePrecedence() {
        for (op in RootOperation.entries) {
            assertEquals(RootGateResult.NotManager, evaluateRootGate(op, facts(manager = false, kernel = null)))
            assertEquals(RootGateResult.SafeMode, evaluateRootGate(op, facts(safe = true, kernel = 2)))
            assertEquals(RootGateResult.RootUnavailable, evaluateRootGate(op, facts(root = false, kernel = 2)))
        }
        assertEquals(InterfaceRelation.Unknown, facts(manager = false).relation)
    }
}
