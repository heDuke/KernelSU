package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.weishu.kernelsu.core.utils.VerifiedArtifact
import me.weishu.kernelsu.data.model.HuskyRelease
import me.weishu.kernelsu.data.model.LkmAsset
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException

class HuskyReleaseRepositoryImpl : HuskyReleaseRepository {

    companion object {
        const val RELEASES_API = "https://api.github.com/repos/heDuke/KernelSU/releases"
        const val RELEASES_PAGE = "https://github.com/heDuke/KernelSU/releases"
        private val LKM_NAME = Regex("""^(android\d+-\d+\.\d+)(?:_(4k|16k|64k))?_kernelsu\.ko$""")
        const val APK_ASSET = "HuskySU.apk"
        const val TAG_PREFIX = "husky-"
        private val TAG_VERSION = Regex("""husky-v(\d+)""")
    }

    override suspend fun fetchLatest(): Result<HuskyRelease> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                throw IOException("Network unavailable")
            }

            val request = Request.Builder()
                .url("$RELEASES_API?per_page=100")
                .header("Accept", "application/vnd.github+json")
                .build()

            ksuApp.okhttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Fetch failed: ${response.code}")
                }
                val releases = parseReleases(JSONArray(response.body.string()))
                releases.maxWithOrNull(
                    compareBy<HuskyRelease> { it.versionCode }.thenBy { it.publishedAt }
                ) ?: throw IOException("No husky release")
            }
        }
    }

    override suspend fun downloadToFile(url: String, dest: File, expectedSize: Long, expectedSha256: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val activeContext = coroutineContext
                val request = Request.Builder().url(url).build()
                val file = ksuApp.okhttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.byteStream().use { input ->
                        VerifiedArtifact.write(input, dest, expectedSize, expectedSha256) { activeContext.ensureActive() }
                    }
                }
                Result.success(file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private fun parseReleases(array: JSONArray): List<HuskyRelease> {
        val result = ArrayList<HuskyRelease>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue
            val tag = item.optString("tag_name", "")
            if (!tag.startsWith(TAG_PREFIX)) continue

            val lkmAssets = ArrayList<LkmAsset>()
            var apkUrl: String? = null
            val assets = item.optJSONArray("assets")
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (url.isEmpty()) continue
                    val match = LKM_NAME.matchEntire(name)
                    if (match != null) {
                        val pages = when (match.groupValues[2]) { "4k" -> 4096; "16k" -> 16384; "64k" -> 65536; else -> null }
                        lkmAssets += LkmAsset(name, url, match.groupValues[1], pages,
                            asset.optLong("size", 0), asset.optString("digest", "").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
                    }
                    if (name == APK_ASSET) apkUrl = url
                }
            }
            if (lkmAssets.isEmpty()) continue

            val match = TAG_VERSION.find(tag)
            val versionCode = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            result += HuskyRelease(
                tag = tag,
                name = item.optString("name", tag),
                htmlUrl = item.optString("html_url", RELEASES_PAGE),
                publishedAt = item.optString("published_at", ""),
                body = item.optString("body", ""),
                versionCode = versionCode,
                lkmAssets = lkmAssets.toList(),
                apkDownloadUrl = apkUrl,
            )
        }
        return result
    }
}
