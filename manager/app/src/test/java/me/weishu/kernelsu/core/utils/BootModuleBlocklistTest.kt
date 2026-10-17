package me.weishu.kernelsu.core.utils
import org.junit.Assert.*
import org.junit.Test
class BootModuleBlocklistTest {
    @Test fun preservesOnlyExplicitBoundedModuleNames() {
        assertEquals(emptyList<String>(), parseBootModuleBlocklist(""))
        assertEquals(listOf("module_a", "module_b"), parseBootModuleBlocklist("module_a, module_b,module_a"))
        for (input in listOf("a;reboot", "\$(id)", "foo,", "-a", "x".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) { parseBootModuleBlocklist(input) }
        }
    }
}
