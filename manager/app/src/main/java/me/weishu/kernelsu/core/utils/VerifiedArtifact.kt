package me.weishu.kernelsu.core.utils

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A failed download must never replace an already verified destination. */
object VerifiedArtifact {
    fun write(input: InputStream, destination: File, expectedSize: Long, expectedSha256: String, checkActive: () -> Unit = {}): File {
        require(expectedSize > 0 && expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Missing artifact integrity metadata" }
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Invalid destination")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create artifact directory")
        val temporary = File.createTempFile("artifact-", ".part", parent)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count.toLong() > expectedSize - total) throw IOException("Artifact exceeds expected size")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    total += count
                }
                if (total != expectedSize) throw IOException("Artifact size mismatch")
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) throw IOException("Artifact SHA-256 mismatch")
                checkActive()
                output.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return destination
        } finally {
            temporary.delete()
        }
    }
}
