package me.weishu.kernelsu.core.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Input description only: never resolves symbols, patches bytes or authorizes loading. */
data class BootInputInspection(
    val format: String,
    val release: String? = null,
    val kmi: String? = null,
    val kernelPageSize: Int? = null,
    val imagePageSize: Int? = null,
    val signaturePresent: Boolean? = null,
    val partition: String? = null,
)

fun inspectBootInput(bytes: ByteArray, totalSize: Long = bytes.size.toLong()): BootInputInspection {
    require(totalSize in 1..536_870_912 && bytes.size >= 44) { "Truncated or oversized input" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun u32(offset: Int) = buffer.getInt(offset).toLong() and 0xffffffffL
    if (bytes.copyOfRange(0, 8).contentEquals("ANDROID!".toByteArray())) {
        val version = u32(40)
        require(version in 0..4) { "Unknown Android boot header" }
        val page = if (version >= 3) 4096L else u32(36)
        require(page in 2048..65536 && page and (page - 1) == 0L) { "Invalid image alignment" }
        val header = if (version >= 3) u32(20) else when (version) { 0L -> 1632L; 1L -> 1648L; else -> 1660L }
        require(header in 44..65536 && header <= totalSize) { "Truncated boot header" }
        if (version >= 3) require(header >= if (version == 4L) 1584 else 1580)
        val kernel = u32(8)
        val ramdisk = u32(if (version >= 3) 12 else 16)
        fun aligned(value: Long) = (value + page - 1) / page * page
        require(kernel + ramdisk > 0 && aligned(header) + aligned(kernel) + ramdisk <= totalSize) { "Truncated boot payload" }
        // Header alignment is not the virtual-memory page size of its kernel.
        return BootInputInspection("Android boot/init_boot v$version", imagePageSize = page.toInt(), partition = if (kernel > 0) "boot" else if (version == 4L) "init_boot" else null)
    }
    if (bytes.copyOfRange(0, 8).contentEquals("VNDRBOOT".toByteArray())) {
        require(u32(8) in 3..4 && totalSize >= 2112) { "Invalid vendor boot header" }
        return BootInputInspection("Android vendor boot; kernel target not established", partition = "vendor_boot")
    }
    require(bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()) { "Unknown boot image/module format" }
    require(totalSize == bytes.size.toLong() && bytes.size <= 16 * 1024 * 1024 && bytes.size >= 64) { "LKM exceeds inspection bound" }
    require(bytes[4].toInt() == 2 && bytes[5].toInt() == 1 && bytes[6].toInt() == 1) { "Requires ELF64 little-endian" }
    require(buffer.getShort(16).toInt() == 1 && buffer.getShort(18).toInt() == 183) { "Requires an ARM64 relocatable module" }
    val offset = buffer.getLong(40)
    val stride = buffer.getShort(58).toInt() and 0xffff
    val count = buffer.getShort(60).toInt() and 0xffff
    val namesIndex = buffer.getShort(62).toInt() and 0xffff
    require(offset >= 64 && stride == 64 && count in 1..4096 && namesIndex in 1 until count && offset <= bytes.size - count * 64L) { "Invalid section table" }
    fun section(index: Int): Pair<Int, Int> {
        val at = (offset + index * 64).toInt()
        val start = buffer.getLong(at + 24); val size = buffer.getLong(at + 32)
        require(start >= 0 && size >= 0 && start <= bytes.size && size <= bytes.size - start) { "Section outside input" }
        return start.toInt() to size.toInt()
    }
    val (namesStart, namesSize) = section(namesIndex)
    var modinfo: Pair<Int, Int>? = null
    for (i in 1 until count) {
        val nameOffset = u32((offset + i * 64).toInt())
        require(nameOffset < namesSize)
        val start = namesStart + nameOffset.toInt()
        val end = (start until namesStart + namesSize).firstOrNull { bytes[it] == 0.toByte() }
        require(end != null && end - start <= 4096) { "Invalid section name" }
        val name = String(bytes, start, end - start, Charsets.US_ASCII)
        if (name == ".modinfo") { require(modinfo == null); modinfo = section(i) }
    }
    val (start, length) = requireNotNull(modinfo) { "Module metadata absent" }
    require(length in 1..65536 && bytes[start + length - 1] == 0.toByte()) { "Invalid module metadata" }
    val fields = linkedMapOf<String, String>()
    String(bytes, start, length, Charsets.UTF_8).split('\u0000').filter { it.contains('=') }.forEach { item ->
        val key = item.substringBefore('='); val value = item.substringAfter('=')
        if (key in setOf("vermagic", "ksu_kernel_release", "ksu_page_shift", "ksu_kmi")) {
            require(key !in fields && value.length <= 1024 && value.none { it.isISOControl() }) { "Ambiguous module metadata" }
            fields[key] = value
        }
    }
    val vermagicRelease = fields["vermagic"]?.substringBefore(' ')
    val declaredRelease = fields["ksu_kernel_release"]
    require(vermagicRelease != null && (declaredRelease == null || declaredRelease == vermagicRelease)) { "Inconsistent module target" }
    val shift = fields["ksu_page_shift"]?.toIntOrNull()
    if (fields.containsKey("ksu_page_shift")) require(shift in setOf(12, 14, 16)) { "Invalid kernel page metadata" }
    val signed = bytes.takeLast(28).toByteArray().contentEquals("~Module signature appended~\n".toByteArray())
    return BootInputInspection("ELF64 ARM64 LKM", declaredRelease ?: vermagicRelease, fields["ksu_kmi"], shift?.let { 1 shl it }, signaturePresent = signed)
}
