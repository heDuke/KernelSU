package me.weishu.kernelsu.data.model

/** Release metadata narrows selection; ksud checks the actual ELF before use. */
data class LkmAsset(
    val name: String,
    val url: String,
    val kmi: String,
    val pageSizeBytes: Int?,
    val sizeBytes: Long,
    val sha256: String?,
)

fun selectLkmAsset(assets: List<LkmAsset>, kmi: String, pageSize: Int): LkmAsset {
    require(pageSize in setOf(4096, 16384, 65536)) { "Unknown kernel page size" }
    val candidates = assets.filter { it.kmi == kmi }
    val exact = candidates.filter { it.pageSizeBytes == pageSize }
    val selected = if (exact.isNotEmpty()) exact else candidates.filter { it.pageSizeBytes == null }
    require(selected.size == 1) { "No unique LKM candidate for $kmi / $pageSize byte pages" }
    return selected.single().also {
        require(it.sizeBytes > 0 && it.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true) {
            "Release asset lacks size/SHA-256 metadata"
        }
    }
}
