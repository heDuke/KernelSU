package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class ExperimentalLoaderSettingsTest {
    @Test fun importsNeverPermitPolicyChangesOrAmbiguousRecords() {
        for (input in listOf("schema=2", "schema=1\nforce=true", "schema=1\ncache_enabled=true",
            "schema=1\nmodule_limit_mib=17", "schema=1\noutput=json\noutput=text",
            "schema=1\ngeneration=bad", "schema=1\ngeneration=-1", "schema=1\nhistory_count=5")) {
            try { ExperimentalLoaderSettings.parse(input.toByteArray()); fail(input) }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun presetReplacementPreservesUnrelatedSavedPreferences() {
        val saved = ExperimentalLoaderSettings.parse("schema=1\npreset=balanced\nio_buffer_kib=64\nmodule_limit_mib=4".toByteArray())
        val lean = saved.withPreset("lean")
        assertEquals("8", lean.effective()["io_buffer_kib"])
        assertEquals("4", lean.effective()["module_limit_mib"])
        assertEquals(lean, ExperimentalLoaderSettings.parse(lean.serialize().toByteArray()))
    }
    @Test fun apiRangeCannotEstablishRootProviderCompatibility() {
        assertEquals(ModuleCompatibility.UNKNOWN, moduleCompatibility(37, 31, 37))
        assertEquals(ModuleCompatibility.UNKNOWN, moduleCompatibility(37, null, null))
        assertEquals(ModuleCompatibility.INCOMPATIBLE, moduleCompatibility(37, null, 34))
        assertEquals(ModuleCompatibility.INCOMPATIBLE, moduleCompatibility(30, 31, null))
    }
}
