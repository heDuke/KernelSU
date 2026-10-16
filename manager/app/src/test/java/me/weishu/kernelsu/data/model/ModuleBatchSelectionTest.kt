package me.weishu.kernelsu.data.model

import me.weishu.kernelsu.ui.screen.home.selectBatchTargets
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleBatchSelectionTest {
    private fun module(id: String, version: Int = 1, enabled: Boolean = false, removed: Boolean = false) = Module(
        id, id, "author", version.toString(), version, "description", enabled, false, removed,
        "", false, false, false, null, null)

    @Test fun recoveryCannotEnableUnrecordedChangedRemovedOrAlreadyEnabledModules() {
        val modules = listOf(module("recorded"), module("changed", version = 2), module("removed", removed = true),
            module("enabled", enabled = true), module("intentionally-disabled"))
        val recorded = mapOf("recorded" to 1, "changed" to 1, "removed" to 1, "enabled" to 1)
        assertEquals(listOf("recorded"), selectBatchTargets(modules, recorded, restore = true).map { it.id })
        assertEquals(listOf("enabled"), selectBatchTargets(modules, recorded, restore = false).map { it.id })
    }
}
