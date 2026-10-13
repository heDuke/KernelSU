package me.weishu.kernelsu.data.model

/** Release metadata narrows selection; ksud checks the actual ELF before use. */
data class LkmAsset(
    val name: String,
    val url: String,
    val kmi: String,
    val pageSizeBytes: Int?,
    val sizeBytes: Long,
    val sha256: String?,
    val kernelRelease: String? = null,
)

data class LkmTarget(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val kmi: String,
    val kernelRelease: String,
    val pageSizeBytes: Int,
)

/** Bind manifest metadata to the exact downloadable payload, never filename guesses. */
fun applyLkmTargets(assets: List<LkmAsset>, targets: List<LkmTarget>): List<LkmAsset> {
    require(targets.map { it.name }.distinct().size == targets.size) { "Duplicate module target metadata" }
    require(targets.map { it.name }.toSet() == assets.map { it.name }.toSet()) { "Incomplete module target metadata" }
    return assets.map { asset ->
        val target = targets.single { it.name == asset.name }
        require(target.sizeBytes == asset.sizeBytes && target.sha256.equals(asset.sha256, ignoreCase = true) &&
            target.sha256.matches(Regex("[0-9a-fA-F]{64}")) && target.kmi == asset.kmi &&
            target.kernelRelease.isNotBlank() && target.kernelRelease.none { it.isWhitespace() || it == '\u0000' } &&
            target.pageSizeBytes in setOf(4096, 16384, 65536) &&
            (asset.pageSizeBytes == null || asset.pageSizeBytes == target.pageSizeBytes)) {
            "Module target metadata differs from release asset"
        }
        asset.copy(kernelRelease = target.kernelRelease, pageSizeBytes = target.pageSizeBytes)
    }
}

fun selectLkmAsset(assets: List<LkmAsset>, kmi: String, pageSize: Int, kernelRelease: String): LkmAsset {
    require(pageSize in setOf(4096, 16384, 65536)) { "Unknown kernel page size" }
    require(kernelRelease.isNotBlank()) { "Unknown kernel release" }
    val selected = assets.filter { it.kmi == kmi && it.pageSizeBytes == pageSize && it.kernelRelease == kernelRelease }
    require(selected.size == 1) { "No unique verified LKM candidate for $kernelRelease / $pageSize byte pages" }
    return selected.single().also {
        require(it.sizeBytes > 0 && it.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true) {
            "Release asset lacks size/SHA-256 metadata"
        }
    }
}
