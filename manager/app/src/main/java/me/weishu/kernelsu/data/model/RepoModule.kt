package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Author(
    val name: String,
    val link: String,
)

@Immutable
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

@Immutable
data class RepoRelease(val version: String, val publishedAt: String, val asset: ReleaseAsset, val changelog: String)

@Immutable
data class RepoModule(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorList: List<Author>,
    val summary: String,
    val metamodule: Boolean,
    val stargazerCount: Int,
    val updatedAt: String,
    val createdAt: String,
    val latestRelease: String,
    val latestReleaseTime: String,
    val latestVersionCode: Int,
    val latestAsset: ReleaseAsset?,
    val sourceName: String = "",
    val sourceId: String = "legacy",
    val sourceUrl: String = "",
    val readmeUrl: String = "",
    val iconUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val license: String = "",
    val supportUrl: String = "",
    val donateUrl: String = "",
    val notes: String = "",
    val repositoryVerified: Boolean? = null,
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val managerRequirements: String = "",
    val kernelSuUnsupported: Boolean = false,
    val minApi: Int? = null,
    val maxApi: Int? = null,
    val releaseHistory: List<RepoRelease> = emptyList(),
)
