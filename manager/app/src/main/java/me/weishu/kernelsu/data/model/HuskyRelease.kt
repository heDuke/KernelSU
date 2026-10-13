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
)

data class ReleaseManifestAsset(val url: String, val sizeBytes: Long, val sha256: String?)
