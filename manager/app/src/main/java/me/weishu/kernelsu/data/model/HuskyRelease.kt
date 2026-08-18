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
    val lkmDownloadUrl: String?,
    val apkDownloadUrl: String?,
)
