package me.weishu.kernelsu.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootUpgradePolicyTest {
    @Test fun permitsReviewedBootstrapFromTheObservedLegacyInterface() {
        assertTrue(canWriteReviewedBootImage(true, true, false, 2, 6))
        assertTrue(canWriteReviewedBootImage(true, true, false, 6, 6))
    }

    @Test fun neverBypassesAuthorizationRootSafeModeOrUnknownInterfaces() {
        assertFalse(canWriteReviewedBootImage(false, true, false, 2, 6))
        assertFalse(canWriteReviewedBootImage(true, false, false, 2, 6))
        assertFalse(canWriteReviewedBootImage(true, true, true, 2, 6))
        for ((kernel, manager) in listOf(0 to 6, -1 to 6, 7 to 6, 4 to 6, 2 to 7)) {
            assertFalse(canWriteReviewedBootImage(true, true, false, kernel, manager))
        }
    }
}
