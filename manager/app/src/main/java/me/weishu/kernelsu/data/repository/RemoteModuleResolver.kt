package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.weishu.kernelsu.ksuApp
import okhttp3.Request
import java.net.URI

data class RemoteModuleCandidate(val name: String, val url: String, val size: Long = 0, val id: String = "", val version: String = "")

/** Discovery only: no candidate is downloaded or installed by resolving a URL. */
object RemoteModuleResolver {
    private fun web(value: String): URI {
        val uri = URI(value.trim())
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter an HTTPS repository or ZIP URL" }
        return uri
    }
    suspend fun resolve(value: String): List<RemoteModuleCandidate> = withContext(Dispatchers.IO) {
        val uri = web(value)
        if (uri.path.endsWith(".zip", true)) return@withContext listOf(RemoteModuleCandidate(uri.path.substringAfterLast('/'), uri.toString()))
        val parts = uri.path.trim('/').split('/')
        val url = if (uri.host.equals("github.com", true) && parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_.-]+")) }) {
            "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases?per_page=30"
        } else uri.toString()
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Repository HTTP ${response.code}" }
            check(response.request.url.isHttps) { "Repository redirected to an insecure URL" }
            val bytes = response.body.byteStream().use { it.readNBytes(8 * 1024 * 1024 + 1) }
            check(bytes.size <= 8 * 1024 * 1024) { "Repository index is too large" }
            parse(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))).also { check(it.isNotEmpty()) { "No downloadable ZIP files found" } }
        }
    }
    internal fun parse(document: JsonElement): List<RemoteModuleCandidate> {
        val result = ArrayList<RemoteModuleCandidate>()
        fun text(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun add(obj: JsonObject, nameKey: String, urlKey: String, id: String = "", version: String = "") {
            val url = text(obj, urlKey)
            if (url.isBlank() || runCatching { web(url) }.isFailure) return
            val name = text(obj, nameKey).ifBlank { URI(url).path.substringAfterLast('/') }.substringAfterLast('/').substringAfterLast('\\')
            if (!name.endsWith(".zip", true)) return
            result += RemoteModuleCandidate(name, url, (obj["size"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0, id, version)
        }
        when (document) {
            is JsonArray -> document.take(30).mapNotNull { it as? JsonObject }.filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true && it["prerelease"]?.jsonPrimitive?.booleanOrNull != true }.forEach { release ->
                (release["assets"] as? JsonArray)?.take(100)?.mapNotNull { it as? JsonObject }?.forEach { add(it, "name", "browser_download_url", version = text(release, "tag_name")) }
            }
            is JsonObject -> {
                val modules = document["modules"] as? JsonArray
                if (modules != null) modules.take(10000).mapNotNull { it as? JsonObject }.forEach { module ->
                    val versions = module["versions"] as? JsonArray ?: JsonArray(listOf(module))
                    versions.take(30).mapNotNull { it as? JsonObject }.forEach { add(it, "zipName", "zipUrl", text(module, "id"), text(it, "version")) }
                } else if (document["assets"] is JsonArray) {
                    document["assets"]!!.jsonArray.mapNotNull { it as? JsonObject }.forEach { add(it, "name", "browser_download_url") }
                } else add(document, "name", "zipUrl", version = text(document, "version"))
            }
            else -> error("Unsupported repository index")
        }
        return result.distinctBy { it.url }.take(100)
    }
}
