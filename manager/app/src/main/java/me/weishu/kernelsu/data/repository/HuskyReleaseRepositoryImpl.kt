package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.weishu.kernelsu.core.utils.VerifiedArtifact
import me.weishu.kernelsu.data.model.HuskyRelease
import me.weishu.kernelsu.data.model.LkmAsset
import me.weishu.kernelsu.data.model.LkmTarget
import me.weishu.kernelsu.data.model.ReleaseManifestAsset
import me.weishu.kernelsu.data.model.applyLkmTargets
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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
        try {
            if (!isNetworkAvailable(ksuApp)) {
                throw IOException("Network unavailable")
            }

            val request = Request.Builder()
                .url("$RELEASES_API?per_page=100")
                .header("Accept", "application/vnd.github+json")
                .build()

            val release = ksuApp.okhttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ReleaseHttpException(response.code,
                        response.code == 429 || (response.code == 403 &&
                            (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null)),
                        response.header("Retry-After")?.toLongOrNull())
                }
                val releases = parseReleases(JSONArray(response.body.string()))
                releases.maxWithOrNull(
                    compareBy<HuskyRelease> { it.versionCode }.thenBy { it.publishedAt }
                ) ?: throw IOException("No husky release")
            }
            Result.success(loadTargetMetadata(release))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("HuskyRelease", "Release request failed", error)
            Result.failure(error)
        }
    }

    private suspend fun loadTargetMetadata(release: HuskyRelease): HuskyRelease {
        if (release.lkmAssets.isEmpty()) return release
        return try {
            val asset = requireNotNull(release.manifestAsset) { "Release has no target manifest" }
            require(asset.sizeBytes in 1L..262144L && asset.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true) {
                "Release target manifest cannot be verified"
            }
            val file = File(ksuApp.cacheDir, "husky-targets/${asset.sha256}.json")
            downloadToFile(asset.url, file, asset.sizeBytes, requireNotNull(asset.sha256)).getOrThrow()
            val manifest = JSONObject(file.readText())
            require(manifest.getInt("schema_version") == 2) { "Release has no exact module targets" }
            val array = manifest.getJSONArray("module_targets")
            val targets = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LkmTarget(item.getString("name"), item.getLong("size"), item.getString("sha256"),
                    item.getString("kmi"), item.getString("kernel_release"), item.getInt("page_size"))
            }
            release.copy(lkmAssets = applyLkmTargets(release.lkmAssets, targets))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("HuskyRelease", "LKM target manifest rejected", error)
            // Keep the independent APK update even when the LKM target is unknown.
            release.copy(lkmAssets = emptyList(), lkmMetadataError = ksuApp.getString(R.string.husky_lkm_metadata_unavailable),
                lkmMetadataDiagnostic = "Release ${release.tag}\n" + error.stackTraceToString().take(16384))
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
            var manifestAsset: ReleaseManifestAsset? = null
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
                    if (name == "manifest.json") manifestAsset = ReleaseManifestAsset(url,
                        asset.optLong("size", 0), asset.optString("digest", "").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
                }
            }
            if (lkmAssets.isEmpty() && apkUrl == null) continue

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
                manifestAsset = manifestAsset,
            )
        }
        return result
    }
}
