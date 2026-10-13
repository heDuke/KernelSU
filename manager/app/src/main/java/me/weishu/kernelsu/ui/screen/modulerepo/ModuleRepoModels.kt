package me.weishu.kernelsu.ui.screen.modulerepo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReleaseAssetArg(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int
) : Parcelable

@Parcelize
data class ReleaseArg(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val assets: List<ReleaseAssetArg>,
    val descriptionHTML: String
) : Parcelable

@Parcelize
data class AuthorArg(
    val name: String,
    val link: String,
) : Parcelable

@Parcelize
data class RepoModuleArg(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorsList: List<AuthorArg>,
    val latestRelease: String,
    val latestReleaseTime: String,
    val releases: List<ReleaseArg>,
    val summary: String = "",
    val sourceName: String = "",
    val sourceId: String = "legacy",
    val installedVersion: String = "",
    val installedVersionCode: Int = 0,
    val latestVersionCode: Int = 0,
    val sourceUrl: String = "",
    val readmeUrl: String = "",
    val iconUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val license: String = "",
    val supportUrl: String = "",
    val donateUrl: String = "",
    val notes: String = "",
    val repositoryVerified: Boolean? = null,
    val metamodule: Boolean = false,
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val managerRequirements: String = "",
    val kernelSuUnsupported: Boolean = false,
    val minApi: Int? = null,
    val maxApi: Int? = null,
) : Parcelable

fun me.weishu.kernelsu.data.model.RepoModule.toModuleArg() = RepoModuleArg(
    moduleId = moduleId, moduleName = moduleName, authors = authors,
    authorsList = authorList.map { AuthorArg(it.name, it.link) },
    latestRelease = latestRelease, latestReleaseTime = latestReleaseTime,
    releases = releaseHistory.map { release ->
        ReleaseArg(release.version, release.version, release.publishedAt,
            listOf(ReleaseAssetArg(release.asset.name, release.asset.downloadUrl, release.asset.size, 0)), release.changelog)
    }.ifEmpty { latestAsset?.let {
        listOf(ReleaseArg(latestRelease, latestRelease, latestReleaseTime,
            listOf(ReleaseAssetArg(it.name, it.downloadUrl, it.size, 0)), ""))
    } ?: emptyList() },
    summary = summary, sourceName = sourceName, sourceId = sourceId, sourceUrl = sourceUrl,
    latestVersionCode = latestVersionCode,
    readmeUrl = readmeUrl, iconUrl = iconUrl, screenshots = screenshots,
    minApi = minApi, maxApi = maxApi, license = license, supportUrl = supportUrl, donateUrl = donateUrl,
    notes = notes, permissions = permissions, dependencies = dependencies,
    repositoryVerified = repositoryVerified, metamodule = metamodule, categories = categories,
    managerRequirements = managerRequirements, kernelSuUnsupported = kernelSuUnsupported,
)
