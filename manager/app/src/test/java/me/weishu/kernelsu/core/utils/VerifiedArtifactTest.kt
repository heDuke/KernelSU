package me.weishu.kernelsu.core.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class VerifiedArtifactTest {
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun verifiedDownloadPromotesOnlyCompleteBytes() {
        val directory = Files.createTempDirectory("artifact-test-").toFile()
        try {
            val destination = directory.resolve("module.ko")
            destination.writeText("old verified content")
            val content = "new verified content".toByteArray()
            VerifiedArtifact.write(ByteArrayInputStream(content), destination, content.size.toLong(), hash(content))
            assertArrayEquals(content, destination.readBytes())
            assertEquals(listOf("module.ko"), directory.list()!!.toList())
        } finally { directory.deleteRecursively() }
    }

    @Test fun sizeHashAndCancellationFailuresPreservePreviousFile() {
        val directory = Files.createTempDirectory("artifact-test-").toFile()
        try {
            val destination = directory.resolve("module.ko")
            val old = "last verified artifact"
            val content = "replacement".toByteArray()
            for (failure in 0..3) {
                destination.writeText(old)
                try {
                    VerifiedArtifact.write(ByteArrayInputStream(content), destination,
                        content.size.toLong() + when (failure) { 0 -> 1; 1 -> -1; else -> 0 },
                        if (failure == 2) "0".repeat(64) else hash(content)) {
                        if (failure == 3) throw IOException("cancelled")
                    }
                    fail("Failed input was accepted")
                } catch (_: IOException) { }
                assertEquals(old, destination.readText())
                assertEquals(listOf("module.ko"), directory.list()!!.toList())
            }
        } finally { directory.deleteRecursively() }
    }
}
