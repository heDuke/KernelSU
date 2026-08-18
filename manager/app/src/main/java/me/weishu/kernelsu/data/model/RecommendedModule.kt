package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecommendedModule(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val homepage: String,
    val note: String,
) {
    val hasDownload: Boolean
        get() = downloadUrl.isNotBlank()

    val zipFileName: String
        get() {
            val last = downloadUrl.substringAfterLast('/').substringBefore('?')
            return if (last.endsWith(".zip", ignoreCase = true)) last else "$id.zip"
        }
}
