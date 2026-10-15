package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class HomeDashboardTest {
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
