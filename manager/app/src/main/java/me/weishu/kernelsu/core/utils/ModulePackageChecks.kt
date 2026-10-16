package me.weishu.kernelsu.core.utils

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.tukaani.xz.LZMAInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.zip.CRC32

/** Check the immutable staged input before passing it to the privileged installer. */
data class ModulePackageInfo(val id: String, val metamodule: Boolean)

/**
 * ZIP compression methods accepted by ksud (userspace/ksud/Cargo.toml `zip` features:
 * deflate, deflate64, lzma, xz; stored is always available). Keep in sync with that file.
 */
val KSUD_SUPPORTED_ZIP_METHODS: Map<Int, String> = linkedMapOf(
    0 to "Stored", 8 to "Deflate", 9 to "Deflate64", 14 to "LZMA", 95 to "XZ",
)

private val KNOWN_ZIP_METHODS = mapOf(
    1 to "Shrink", 6 to "Implode", 12 to "bzip2", 19 to "LZ77", 93 to "zstd", 96 to "JPEG", 97 to "WavPack", 98 to "PPMd", 99 to "AES",
)

fun zipMethodName(method: Int): String =
    KSUD_SUPPORTED_ZIP_METHODS[method] ?: KNOWN_ZIP_METHODS[method] ?: "method $method"

/** The package uses a compression method the installer cannot extract. */
class UnsupportedZipMethodException(val method: Int, val entryName: String) :
    IllegalStateException("Unsupported ZIP compression method ${zipMethodName(method)} ($method) in $entryName")

fun validateModulePackage(file: File, expectedSize: Long, expectedId: String): ModulePackageInfo {
    check(expectedSize <= 0 || file.length() == expectedSize) { "Package size mismatch" }
    return ZipFile.builder().setFile(file).get().use { zip ->
        for (entry in zip.entries) {
            if (entry.method !in KSUD_SUPPORTED_ZIP_METHODS) throw UnsupportedZipMethodException(entry.method, entry.name)
        }
        zip.getEntry("module.prop")?.let { entry ->
            val properties = Properties()
            openEntry(zip, entry).use { input ->
                val bytes = input.readNBytes(65537)
                check(bytes.size <= 65536) { "Module metadata is too large" }
                val crc = CRC32().apply { update(bytes) }.value
                check(entry.crc == crc) { "Module metadata ZIP CRC mismatch" }
                properties.load(bytes.inputStream())
            }
            val id = properties.getProperty("id", "")
            check(expectedId.isBlank() || id == expectedId) { "Module ID mismatch" }
            ModulePackageInfo(id, properties.getProperty("metamodule", "0") == "1")
        } ?: run {
            check(expectedId.isBlank()) { "Module ID cannot be verified: module.prop is missing" }
            ModulePackageInfo("", false)
        }
    }
}

private fun openEntry(zip: ZipFile, entry: ZipArchiveEntry): InputStream {
    if (entry.method != 14) {
        if (!zip.canReadEntryData(entry)) throw UnsupportedZipMethodException(entry.method, entry.name)
        return zip.getInputStream(entry)
    }
    // ZIP LZMA (APPNOTE 5.8): 2-byte version, 2-byte props length, 5-byte props, raw LZMA stream.
    val raw = DataInputStream(zip.getRawInputStream(entry))
    raw.skipNBytes(2)
    val propsLength = raw.readUnsignedByte() or (raw.readUnsignedByte() shl 8)
    check(propsLength == 5) { "Corrupt LZMA entry ${entry.name}" }
    val props = raw.readUnsignedByte()
    var dict = 0
    for (i in 0 until 4) dict = dict or (raw.readUnsignedByte() shl (8 * i))
    val size = if (entry.generalPurposeBit.usesDataDescriptor() && entry.size < 0) -1L else entry.size
    return LZMAInputStream(raw, size, props.toByte(), dict)
}
