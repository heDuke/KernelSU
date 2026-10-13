package me.weishu.kernelsu.core.utils

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadTargetTest {
    @Test fun traversalAndCollisionsNeverOverwriteExistingFiles() {
        val directory = Files.createTempDirectory("download-target").toFile()
        try {
            val first = reserveDownloadTarget(directory, "../module.zip")
            first.writeText("keep")
            val second = reserveDownloadTarget(directory, "module.zip")
            assertEquals(directory.canonicalFile, first.canonicalFile.parentFile)
            assertEquals("keep", first.readText())
            assertNotEquals(first, second)
            assertEquals(0L, second.length())
            val unusual = reserveDownloadTarget(directory, "😀".repeat(200) + ".zip")
            assertTrue(unusual.name.toByteArray().size < 255)
        } finally { directory.deleteRecursively() }
    }
}
