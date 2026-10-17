package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class HuskyRelease(
    val tag: String,
    val name: String,
    val htmlUrl: String,
    val publishedAt: String,
    val body: String,
    val versionCode: Long,
    val lkmAssets: List<LkmAsset>,
    val apkDownloadUrl: String?,
    val manifestAsset: ReleaseManifestAsset? = null,
    val lkmMetadataError: String? = null,
    val lkmMetadataDiagnostic: String? = null,
)

data class ReleaseManifestAsset(val url: String, val sizeBytes: Long, val sha256: String?)

enum class LkmUpdateAvailability { Available, UpToDate, NoMatchingTarget, MetadataRejected, DeviceUnknown }
data class LkmUpdateAssessment(val availability: LkmUpdateAvailability, val candidate: LkmAsset? = null, val diagnostic: String? = null)

/** Separate unsupported targets from invalid publication data, without weakening selection. */
fun assessLkmUpdate(release: HuskyRelease, installed: Long, kmi: String, pageSize: Int, kernelRelease: String): LkmUpdateAssessment {
    if (release.lkmMetadataError != null) return LkmUpdateAssessment(LkmUpdateAvailability.MetadataRejected,
        diagnostic = release.lkmMetadataDiagnostic ?: release.lkmMetadataError)
    if (kmi.isBlank() || kernelRelease.isBlank() || pageSize !in setOf(4096, 16384, 65536))
        return LkmUpdateAssessment(LkmUpdateAvailability.DeviceUnknown, diagnostic = "Unknown device target: $kmi / $kernelRelease / $pageSize")
    if (release.lkmAssets.none { it.kmi == kmi && it.pageSizeBytes == pageSize && it.kernelRelease == kernelRelease })
        return LkmUpdateAssessment(LkmUpdateAvailability.NoMatchingTarget,
            diagnostic = "Release ${release.tag}; device $kmi / $kernelRelease / $pageSize; published targets: " +
                release.lkmAssets.joinToString { "${it.kmi} / ${it.kernelRelease} / ${it.pageSizeBytes}" })
    return try {
        val asset = selectLkmAsset(release.lkmAssets, kmi, pageSize, kernelRelease)
        LkmUpdateAssessment(if (release.versionCode == 0L || release.versionCode > installed)
            LkmUpdateAvailability.Available else LkmUpdateAvailability.UpToDate, asset)
    } catch (error: IllegalArgumentException) {
        LkmUpdateAssessment(LkmUpdateAvailability.MetadataRejected, diagnostic = error.stackTraceToString())
    }
}
