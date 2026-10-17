package me.weishu.kernelsu.core.utils

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModulePackageChecksTest {
    private val prop = "id=fixture\nmetamodule=1\n".toByteArray()

    private fun temp() = Files.createTempFile("module-test", ".zip").toFile()

    /** Writes a single pre-compressed entry with an arbitrary method id. */
    private fun rawZip(file: File, method: Int, payload: ByteArray, name: String = "module.prop", extra: Map<String, ByteArray> = emptyMap()) {
        ZipArchiveOutputStream(file).use { zip ->
            val entry = ZipArchiveEntry(name).apply {
                this.method = method; size = prop.size.toLong(); compressedSize = payload.size.toLong()
                crc = CRC32().apply { update(prop) }.value
            }
            zip.addRawArchiveEntry(entry, payload.inputStream())
            for ((n, data) in extra) {
                zip.putArchiveEntry(ZipArchiveEntry(n)); zip.write(data); zip.closeArchiveEntry()
            }
        }
    }

    private fun xz(data: ByteArray) = ByteArrayOutputStream().also { o -> XZOutputStream(o, LZMA2Options()).use { it.write(data) } }.toByteArray()

    private fun zipLzma(data: ByteArray): ByteArray {
        val options = LZMA2Options()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(9, 20, 5, 0))
        val body = ByteArrayOutputStream()
        LZMAOutputStream(body, options, false).use { it.write(data) }
        val b = body.toByteArray()
        val props = ((options.pb * 5 + options.lp) * 9 + options.lc).toByte()
        val dict = options.dictSize
        out.write(byteArrayOf(props, dict.toByte(), (dict shr 8).toByte(), (dict shr 16).toByte(), (dict shr 24).toByte()))
        out.write(b)
        return out.toByteArray()
    }

    @Test fun rejectedInputsCannotReachPrivilegedInstaller() {
        val file = temp()
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
            catch (_: ModulePackageException) { }
        } finally { file.delete() }
    }

    @Test fun acceptsXzCompressedModuleProp() {
        val file = temp()
        try {
            rawZip(file, 95, xz(prop), extra = mapOf("customize.sh" to "echo hi\n".toByteArray()))
            assertEquals(ModulePackageInfo("fixture", true), validateModulePackage(file, file.length(), "fixture"))
            try { validateModulePackage(file, 0, "other"); fail("Accepted wrong id") } catch (_: IllegalStateException) { }
        } finally { file.delete() }
    }

    @Test fun acceptsXzEntryOtherThanModuleProp() {
        val file = temp()
        try {
            ZipArchiveOutputStream(file).use { zip ->
                val payload = xz("binary".toByteArray())
                zip.addRawArchiveEntry(ZipArchiveEntry("system/bin/tool").apply {
                    method = 95; size = 6; compressedSize = payload.size.toLong(); crc = CRC32().apply { update("binary".toByteArray()) }.value
                }, payload.inputStream())
                zip.putArchiveEntry(ZipArchiveEntry("module.prop")); zip.write(prop); zip.closeArchiveEntry()
            }
            assertEquals("fixture", validateModulePackage(file, 0, "fixture").id)
        } finally { file.delete() }
    }

    @Test fun acceptsLzmaCompressedModuleProp() {
        val file = temp()
        try {
            rawZip(file, 14, zipLzma(prop))
            assertEquals("fixture", validateModulePackage(file, 0, "fixture").id)
        } finally { file.delete() }
    }

    @Test fun rejectsXzWithCorruptCrc() {
        val file = temp()
        try {
            val other = "id=evil\n".toByteArray()
            rawZip(file, 95, xz(other))
            try { validateModulePackage(file, 0, ""); fail("Accepted CRC mismatch") } catch (_: Exception) { }
        } finally { file.delete() }
    }

    @Test fun rejectsMethodsKsudCannotExtract() {
        for ((method, name) in listOf(93 to "zstd", 12 to "bzip2", 98 to "PPMd")) {
            val file = temp()
            try {
                rawZip(file, method, byteArrayOf(1, 2, 3, 4))
                val e = try { validateModulePackage(file, 0, "fixture"); null } catch (e: UnsupportedZipMethodException) { e }
                assertNotNull("method $method accepted", e)
                assertEquals(method, e!!.method)
                assertEquals(name, zipMethodName(method))
            } finally { file.delete() }
        }
    }

    @Test fun supportedListMatchesKsud() {
        assertEquals(listOf(0, 8, 9, 14, 95), KSUD_SUPPORTED_ZIP_METHODS.keys.toList())
    }
    @Test fun ioAndPermissionFailuresAreNotReportedAsCorruptArchives() {
        assertEquals(ModuleInstallFailure.ReadWrite, classifyModuleInstallFailure(IOException("storage unavailable")))
        assertEquals(ModuleInstallFailure.Permission, classifyModuleInstallFailure(SecurityException("grant expired")))
        assertEquals(ModuleInstallFailure.Download, classifyModuleInstallFailure(ModuleDownloadException()))
        assertEquals(ModuleInstallFailure.InvalidPackage,
            classifyModuleInstallFailure(ModulePackageException(ModulePackageFailure.InvalidArchive, "bad ZIP")))
    }

}
