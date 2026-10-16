package me.weishu.kernelsu.data.model

import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.repository.sameActivePolicy
import org.junit.Assert.*
import org.junit.Test

class SuperUserPolicyTest {
    private val first = AuthorizationIdentity("app.first", 10123, 1, 100, 200)
    private val sibling = AuthorizationIdentity("app.second", 10123, 2, 110, 210)
    private val reviewed = AuthorizationTarget(10123, setOf(first, sibling), false)

    @Test fun changedSharedUidMembershipRequiresReview() {
        assertTrue(reviewed.matches(setOf(sibling, first)))
        assertFalse(reviewed.matches(setOf(first)))
        assertFalse(reviewed.matches(setOf(first, sibling, first.copy(packageName = "app.new"))))
    }

    @Test fun reinstallUpdateAndUidReuseCannotReuseConfirmation() {
        listOf(first.copy(firstInstallTime = 300), first.copy(versionCode = 3),
            first.copy(lastUpdateTime = 400), first.copy(uid = 10124),
            first.copy(packageName = "app.replacement")).forEach {
            assertFalse(reviewed.matches(setOf(it, sibling)))
        }
        assertFalse(AuthorizationTarget(10123, emptySet(), false).matches(emptySet()))
        assertFalse(reviewed.copy(uid = 10124).matches(reviewed.members))
    }

    @Test fun restrictedSystemAppIdsAreRestrictedForEveryAndroidUser() {
        assertTrue(canGrantRoot(1000))
        assertTrue(canGrantRoot(2000))
        assertTrue(canGrantRoot(10123))
        assertTrue(canGrantRoot(110123))
        assertTrue(canGrantRoot(101000))
        assertFalse(canGrantRoot(1001))
        assertFalse(canGrantRoot(101001))
        assertFalse(canGrantRoot(100000))
        assertFalse(canGrantRoot(-1))
    }

    @Test fun corruptOrUnknownFilterRestoresGrantedDefault() {
        assertEquals(SuperUserFilter.Granted, SuperUserFilter.fromKey(null))
        assertEquals(SuperUserFilter.Granted, SuperUserFilter.fromKey("future"))
        assertEquals(SuperUserFilter.All, SuperUserFilter.fromKey("all"))
        assertEquals(SuperUserFilter.Custom, SuperUserFilter.fromKey("custom"))
    }

    @Test fun driverAbsenceAndManagerAuthorizationAreDifferentPrerequisites() {
        assertEquals(SuperUserFailure.DriverUnavailable, SuperUserAccessSnapshot(-1, false, 0, 1).failure)
        assertEquals(SuperUserFailure.DriverUnavailable, SuperUserAccessSnapshot(0, false, 0, 1).failure)
        assertEquals(SuperUserFailure.ManagerUnauthorized, SuperUserAccessSnapshot(32642, false, 1, 1).failure)
        assertEquals(SuperUserFailure.ManagerUnauthorized, SuperUserAccessSnapshot(32642, false, 0, 1).failure)
    }

    @Test fun unknownAndMismatchedInterfacesStayBlockedWithoutWeakeningAuthorization() {
        assertEquals(SuperUserFailure.InterfaceMismatch, SuperUserAccessSnapshot(32642, true, 0, 1).failure)
        assertEquals(SuperUserFailure.InterfaceMismatch, SuperUserAccessSnapshot(32642, true, 2, 1).failure)
        assertEquals(SuperUserFailure.InterfaceMismatch, SuperUserAccessSnapshot(32642, true, 0, 0).failure)
        assertNull(SuperUserAccessSnapshot(32642, true, 1, 1).failure)
    }

    @Test fun diagnosticsRetainProbeValuesAndReportUnavailableRootServiceSeparately() {
        val access = SuperUserAccessSnapshot(32642, true, 1, 1)
        val error = SuperUserException(SuperUserFailure.ServiceTimeout, access)
        val report = error.diagnosticReport()
        assertTrue(report.contains("reason=ServiceTimeout"))
        assertTrue(report.contains("driver_version=32642"))
        assertTrue(report.contains("manager_authorized=true"))
        assertTrue(report.contains("kernel_uapi=1"))
        assertTrue(report.contains("manager_uapi=1"))
        assertFalse(SuperUserException(SuperUserFailure.RootUnavailable).diagnosticReport().contains("driver_version=0"))
    }

    @Test fun readbackChecksActivePolicyAndIgnoresInactiveUnionAndExternalRules() {
        val root = Natives.Profile("app.first", 10123, allowSu = true, rootUseDefault = false,
            groups = listOf(0, 1000), capabilities = listOf(1, 2), rules = "external")
        assertTrue(sameActivePolicy(root, root.copy(nonRootUseDefault = false, umountModules = false, rules = "")))
        assertTrue(sameActivePolicy(root, root.copy(capabilities = listOf(2, 1))))
        assertFalse(sameActivePolicy(root, root.copy(allowSu = false)))
        assertFalse(sameActivePolicy(root, root.copy(flags = 0)))
        assertFalse(sameActivePolicy(root, root.copy(groups = listOf(0))))
        assertFalse(sameActivePolicy(root, root.copy(currentUid = 10124)))
        val nonRoot = root.copy(allowSu = false, nonRootUseDefault = false)
        assertTrue(sameActivePolicy(nonRoot, nonRoot.copy(groups = emptyList(), capabilities = emptyList())))
        assertFalse(sameActivePolicy(nonRoot, nonRoot.copy(umountModules = false)))
    }
}
