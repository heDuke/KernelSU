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
    val size: Long,
    val sha256: String = "",
)

@Immutable
data class RepoRelease(val version: String, val publishedAt: String,
    val asset: ReleaseAsset = ReleaseAsset("", "", 0), val changelog: String = "",
    val tag: String = version, val name: String = version, val versionCode: Int = 0,
    val changelogHtml: String? = null, val zipUrl: String = asset.downloadUrl,
    val zipName: String = asset.name, val zipSize: Long = asset.size,
    val sha256: String = "", val tagCommit: String = "", val releaseUrl: String = "",
    val attestationUrl: String? = null, val immutable: Boolean = false)

@Immutable
data class RepoModule(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorList: List<Author>,
    val summary: String,
    val metamodule: Boolean,
    val stargazerCount: Int = 0,
    val updatedAt: String = "",
    val createdAt: String = "",
    val latestRelease: String = "",
    val latestReleaseTime: String,
    val latestVersionCode: Int,
    val latestAsset: ReleaseAsset? = null,
    val tags: List<String> = emptyList(),
    val isScript: Boolean = false,
    val latestVersion: String = latestRelease,
    val latestReleaseEpoch: Long = 0,
    val stars: Int? = null,
    val starsRepository: String? = null,
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

@Immutable
data class RepoModuleList(
    val modules: List<RepoModule>,
    val buildId: String,
    /** True when the list is the last good cached copy because the network copy failed. */
    val stale: Boolean,
    val error: Throwable?,
)


@Immutable
data class RepoModuleDetail(
    val module: RepoModule,
    val license: String?,
    val homepage: String?,
    val support: String?,
    val sourceFullName: String,
    val sourceUrl: String,
    val sourceCommit: String,
    val readmeHtml: String?,
    /** The module has a README but it could not be loaded (show a retry, not "no README"). */
    val readmeFailed: Boolean,
    /** Base URL relative resources in the readme are resolved against (the site root). */
    val readmeBaseUrl: String,
    val releases: List<RepoRelease>,
    val healthStale: Boolean,
    val webUrl: String,
    val fromCache: Boolean,
)
