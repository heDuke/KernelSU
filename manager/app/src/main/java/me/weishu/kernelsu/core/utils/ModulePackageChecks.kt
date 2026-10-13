package me.weishu.kernelsu.core.utils

import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

/** Check the immutable staged input before passing it to the privileged installer. */
data class ModulePackageInfo(val id: String, val metamodule: Boolean)

fun validateModulePackage(file: File, expectedSize: Long, expectedId: String): ModulePackageInfo {
    check(expectedSize <= 0 || file.length() == expectedSize) { "Package size mismatch" }
    return ZipFile(file).use { zip ->
        zip.getEntry("module.prop")?.let { entry ->
            val properties = Properties()
            zip.getInputStream(entry).use { input ->
                val bytes = input.readNBytes(65537)
                check(bytes.size <= 65536) { "Module metadata is too large" }
                val crc = java.util.zip.CRC32().apply { update(bytes) }.value
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
