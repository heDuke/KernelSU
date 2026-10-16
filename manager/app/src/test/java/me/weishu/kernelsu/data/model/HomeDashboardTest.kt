package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class HomeDashboardTest {
    @Test fun pendingModuleChangesNeedAConfirmedReboot() {
        val pending = PendingModuleChanges().changed("count:7")
        assertTrue(pending.pending)
        assertTrue(pending.observe("count:7").pending)
        assertTrue(pending.observe("").pending)
        assertTrue(pending.observe("id:same-boot").pending)
        assertFalse(pending.observe("count:8").pending)
        val unknown = PendingModuleChanges().changed("")
        assertTrue(unknown.observe("count:7").pending)
        assertFalse(unknown.observe("count:7").observe("count:8").pending)
    }

    @Test fun cardSelectionMigratesLegacySettingsAndPreservesNewOrder() {
        val layout = DashboardLayout(cards = listOf(DashboardCard.Device, DashboardCard.Compatibility, DashboardCard.Status),
            shortcuts = listOf(DashboardShortcut.Loader))
        assertEquals(layout, DashboardLayout.parse(layout.serialize()))
        val legacy = layout.serialize().lines().take(3).toMutableList().also { it[0] = "1" }.joinToString("\n")
        val migrated = DashboardLayout.parse(legacy)
        assertEquals(layout.sizes, migrated.sizes)
        assertEquals(layout.shortcuts, migrated.shortcuts)
        assertEquals(DashboardLayout().cards, migrated.cards)
        for (cards in listOf("Status", "Compatibility,Compatibility", "Compatibility,Missing", "")) {
            val malformed = layout.serialize().lines().take(3).joinToString("\n") + "\n" + cards
            assertThrows(IllegalArgumentException::class.java) { DashboardLayout.parse(malformed) }
        }
    }

    @Test fun mixedSizesKeepReadingOrderAndPairCompactCardsWithoutOmissions() {
        val layout = DashboardLayout(sizes = DashboardCard.entries.associateWith { DashboardSize.Compact } + mapOf(
            DashboardCard.Access to DashboardSize.Standard, DashboardCard.Versions to DashboardSize.Expanded,
            DashboardCard.Modules to DashboardSize.Expanded), cards = DashboardCard.entries)
        val rows = layout.rows()
        assertEquals(DashboardCard.entries, rows.flatten())
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2), rows.map { it.size })
        assertEquals(listOf(listOf(DashboardCard.Compatibility)), layout.rows(listOf(DashboardCard.Compatibility)))
        assertEquals(emptyList<List<DashboardCard>>(), layout.rows(emptyList()))
    }

    @Test fun settingsPreserveEveryCardAndShortcutOrder() {
        val layout = DashboardLayout(sizes = DashboardLayout().sizes + (DashboardCard.Compatibility to DashboardSize.Expanded),
            shortcuts = listOf(DashboardShortcut.Loader, DashboardShortcut.InstallModule, DashboardShortcut.Diagnostics))
        assertEquals(layout, DashboardLayout.parse(layout.serialize()))
        for (bad in listOf(layout.serialize().replace("Compatibility=Expanded", "Status=Expanded"),
            layout.serialize().replace("Expanded", "Unbounded"), layout.serialize() + ",Loader", "2\n\n", "")) {
            assertThrows(IllegalArgumentException::class.java) { DashboardLayout.parse(bad) }
        }
    }
    @Test fun repeatedFaultsCountOnceUntilObservedRecovery() {
        var state = RuntimeRecord().observe("boot", 1000, 100, true, emptySet())
        state = state.observe("boot", 2000, 200, true, setOf("safe_mode"))
        state = state.observe("boot", 3000, 300, true, setOf("safe_mode"))
        assertEquals(1L, state.total)
        assertNull(state.observedSince)
        state = state.observe("boot", 4000, 400, true, emptySet())
        assertEquals(4000L, state.observedSince)
        state = state.observe("boot", 5000, 500, true, setOf("safe_mode"))
        assertEquals(2L, state.total)
        assertEquals(listOf(200L, 500L), state.events.map { it.at })
    }
    @Test fun gapsPauseAndRebootsNeverExtendNormalTime() {
        val initial = RuntimeRecord().observe("boot", 1000, 100, true, emptySet())
        assertEquals(1000L, initial.observe("boot", 2000, 200, true, emptySet()).observedSince)
        assertEquals(200000L, initial.observe("boot", 200000, 200, true, emptySet()).observedSince)
        assertEquals(2000L, initial.pause().observe("boot", 2000, 200, true, emptySet()).observedSince)
        assertEquals(10L, initial.observe("new-boot", 10, 200, true, emptySet()).observedSince)
        var unknown = initial.observe("", 2000, 200, true, setOf("safe_mode"))
        assertNull(unknown.observedSince)
        unknown = unknown.observe("", 3000, 300, true, setOf("safe_mode"))
        assertEquals(1L, unknown.total)
    }
    @Test fun uninstalledBaselineIsNotAnIncidentAndHistoryIsBounded() {
        assertEquals(0L, RuntimeRecord().observe("boot", 1, 1, false, emptySet()).total)
        var state = RuntimeRecord(total = Long.MAX_VALUE)
        for (i in 1..100) {
            val previous = state.total
            state = state.observe("boot", i * 1000L, i.toLong(), true, if (i % 2 == 0) emptySet() else setOf("safe_mode"))
            assertTrue(state.total >= previous)
        }
        assertTrue(state.total > 0)
        assertEquals(40, state.events.size)
    }
}
