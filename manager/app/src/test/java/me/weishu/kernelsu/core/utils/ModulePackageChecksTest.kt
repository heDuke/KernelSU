package me.weishu.kernelsu.core.utils

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ModulePackageChecksTest {
    @Test fun rejectedInputsCannotReachPrivilegedInstaller() {
        val file = Files.createTempFile("module-test", ".zip").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("module.prop")); zip.write("id=fixture\n".toByteArray()); zip.closeEntry()
            }
            validateModulePackage(file, file.length(), "fixture")
            for (input in listOf((file.length() + 1) to "fixture", file.length() to "other")) {
                try { validateModulePackage(file, input.first, input.second); fail("Accepted invalid package") }
                catch (_: IllegalStateException) { }
            }
            file.writeText("not a ZIP")
            try { validateModulePackage(file, 0, ""); fail("Accepted invalid ZIP") }
            catch (_: java.util.zip.ZipException) { }
        } finally { file.delete() }
    }
}
