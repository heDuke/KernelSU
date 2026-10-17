package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.data.model.Author
import me.weishu.kernelsu.data.model.ReleaseAsset
import me.weishu.kernelsu.data.model.RepoModule
import me.weishu.kernelsu.data.model.RepoRelease
import me.weishu.kernelsu.ksuApp
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

class ModuleRepoRepositoryImpl : ModuleRepoRepository {
    private val preferences = ksuApp.getSharedPreferences("module-repositories", android.content.Context.MODE_PRIVATE)
    private val definitions = listOf(
        me.weishu.kernelsu.data.model.RepoSource("kernelsu", "KernelSU Modules", "https://modules.kernelsu.org/api/v1/index.json"),
        me.weishu.kernelsu.data.model.RepoSource("googlers", "Googlers Magisk Repo", "https://gr.dergoogler.com/gmr/json/modules.json"),
        me.weishu.kernelsu.data.model.RepoSource("alt", "Magisk Modules Alt Repo", "https://magisk-modules-alt-repo.github.io/json-v2/json/modules.json"),
        me.weishu.kernelsu.data.model.RepoSource("izzy", "IzzyOnDroid Magisk Repository", "https://apt.izzysoft.de/magisk/json/modules.json"),
    )
    private val mutableSources = kotlinx.coroutines.flow.MutableStateFlow(definitions.map {
        me.weishu.kernelsu.data.model.RepoSourceStatus(it, preferences.getBoolean(it.id, it.id != "kernelsu"))
    })
    override val sources = mutableSources.asStateFlow()
    override fun setSourceEnabled(id: String, enabled: Boolean) {
        require(definitions.any { it.id == id })
        preferences.edit().putBoolean(id, enabled).apply()
        mutableSources.value = sources.value.map { if (it.source.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun fetchModules(): Result<List<RepoModule>> = withContext(Dispatchers.IO) {
        val statuses = ArrayList<me.weishu.kernelsu.data.model.RepoSourceStatus>()
        val combined = ArrayList<RepoModule>()
        for (source in definitions) {
            val enabled = preferences.getBoolean(source.id, source.id != "kernelsu")
            if (!enabled) { statuses += me.weishu.kernelsu.data.model.RepoSourceStatus(source, false); continue }
            if (source.id == "kernelsu") {
                val result = OfficialRegistryRepository().fetchModules()
                val catalog = result.getOrNull()
                combined += catalog?.modules.orEmpty()
                statuses += me.weishu.kernelsu.data.model.RepoSourceStatus(source, count = catalog?.modules?.size ?: 0,
                    cached = catalog?.stale == true, error = (result.exceptionOrNull() ?: catalog?.error)?.message)
                continue
            }
            val cacheDir = java.io.File(ksuApp.filesDir, "module-repositories").apply { mkdirs() }
            val cache = java.io.File(cacheDir, "${source.id}.json")
            val result = runCatching {
                ksuApp.okhttpClient.newCall(Request.Builder().url(source.indexUrl).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val bytes = response.body.byteStream().use { it.readNBytes(8 * 1024 * 1024 + 1) }
                    check(bytes.size <= 8 * 1024 * 1024) { "Index exceeds size limit" }
                    val root = JSONObject(bytes.toString(Charsets.UTF_8))
                    val parsed = parseModules(root, source)
                    val temporary = java.io.File.createTempFile(source.id, ".part", cacheDir)
                    try {
                        temporary.outputStream().use { it.write(bytes); it.fd.sync() }
                        java.nio.file.Files.move(temporary.toPath(), cache.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally { temporary.delete() }
                    parsed
                }
            }
            val cached = result.isFailure && cache.isFile
            val modules = result.getOrNull() ?: if (cached) runCatching {
                check(cache.length() <= 8 * 1024 * 1024)
                parseModules(JSONObject(cache.readText()), source)
            }.getOrDefault(emptyList()) else emptyList()
            combined += modules
            statuses += me.weishu.kernelsu.data.model.RepoSourceStatus(source, count = modules.size,
                cached = cached, lastSync = if (cache.isFile) cache.lastModified() else 0,
                error = result.exceptionOrNull()?.message)
        }
        mutableSources.value = statuses
        if (statuses.any { it.enabled } && statuses.filter { it.enabled }.all { it.error != null } && combined.isEmpty()) {
            Result.failure(IllegalStateException("All enabled module sources failed"))
        } else Result.success(combined)
    }
    private fun parseModules(root: JSONObject, source: me.weishu.kernelsu.data.model.RepoSource): List<RepoModule> {
                val modules = root.getJSONArray("modules")
                check(modules.length() <= 10000) { "Too many modules in index" }
                return                 (0 until modules.length()).mapNotNull { index ->
                    val item = modules.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id").trim()
                    if (id.isEmpty()) return@mapNotNull null
                    val versions = item.optJSONArray("versions")
                    val latest = (0 until (versions?.length() ?: 0))
                        .mapNotNull { versions?.optJSONObject(it) }
                        .maxByOrNull { it.optLong("versionCode") }
                    val downloadUrl = httpUrl(latest?.optString("zipUrl").orEmpty())
                    val sourceUrl = httpUrl(item.optJSONObject("track")?.optString("source").orEmpty())
                    val screenshots = item.optJSONArray("screenshots") ?: item.optJSONArray("images")
                    val author = item.optString("author", "")
                    val timestamp = item.optLong("timestamp")
                    RepoModule(
                        moduleId = id,
                        moduleName = item.optString("name", id),
                        authors = author,
                        authorList = listOf(Author(author, sourceUrl)),
                        summary = item.optString("description", ""),
                        metamodule = item.optBoolean("metamodule") || item.optString("metamodule") == "1",
                        stargazerCount = item.optInt("stars"),
                        updatedAt = date(timestamp),
                        createdAt = date(item.optLong("added")),
                        latestRelease = latest?.optString("version") ?: item.optString("version", ""),
                        latestReleaseTime = date(timestamp),
                        latestReleaseEpoch = timestamp * 1000,
                        stars = (item.opt("stars") as? Number)?.toInt(),
                        latestVersionCode = latest?.optInt("versionCode") ?: item.optInt("versionCode"),
                        latestAsset = downloadUrl.takeIf { it.isNotEmpty() }?.let {
                            ReleaseAsset(
                                name = it.substringAfterLast('/').substringBefore('?'),
                                downloadUrl = it,
                                size = latest?.optLong("size", item.optLong("size")) ?: item.optLong("size"),
                            )
                        },
                        sourceName = source.name,
                        sourceId = source.id,
                        sourceUrl = sourceUrl,
                        readmeUrl = httpUrl(item.optString("readme", "")),
                        iconUrl = httpUrl(item.optString("icon", "")),
                        screenshots = (0 until (screenshots?.length() ?: 0)).mapNotNull {
                            val image = screenshots?.opt(it)
                            httpUrl(if (image is JSONObject) image.optString("url") else image as? String ?: "")
                                .takeIf(String::isNotEmpty)
                        }.take(12),
                        license = item.optString("license", "").takeUnless { it == "null" }.orEmpty(),
                        supportUrl = httpUrl(item.optString("support", "")),
                        donateUrl = httpUrl(item.optString("donate", "")),
                        notes = item.optJSONObject("note")?.optString("message").orEmpty(),
                        repositoryVerified = if (item.opt("verified") is Boolean) item.optBoolean("verified") else null,
                        permissions = strings(item.optJSONArray("permissions")),
                        dependencies = (strings(item.optJSONArray("require")) + strings(item.optJSONArray("dependencies")) +
                            strings(item.optJSONObject("manager")?.optJSONObject("kernelsu")?.optJSONArray("require"))).distinct(),
                        categories = strings(item.optJSONArray("categories")),
                        managerRequirements = managerDescription(item.optJSONObject("manager")),
                        kernelSuUnsupported = item.optJSONObject("manager")?.optJSONObject("kernelsu")?.optInt("min") == -1,
                        minApi = item.optInt("minApi").takeIf { it > 0 },
                        maxApi = item.optInt("maxApi").takeIf { it > 0 },
                        releaseHistory = (0 until (versions?.length() ?: 0))
                            .mapNotNull { versions?.optJSONObject(it) }
                            .sortedByDescending { it.optLong("versionCode") }
                            .mapNotNull { release ->
                                val url = httpUrl(release.optString("zipUrl"))
                                if (url.isBlank()) null else RepoRelease(
                                    release.optString("version"), date(release.optLong("timestamp")),
                                    ReleaseAsset(url.substringAfterLast('/').substringBefore('?'), url,
                                        release.optLong("size", if (release == latest) item.optLong("size") else 0)),
                                    release.optString("changelog", ""),
                                )
                            },
                    )
                }
    }


    private fun managerDescription(managers: JSONObject?): String {
        val rule = managers?.optJSONObject("kernelsu") ?: return ""
        val min = (rule.opt("min") as? Number)?.toInt()?.takeIf { it > 0 }
        val max = (rule.opt("max") as? Number)?.toInt()?.takeIf { it > 0 }
        return listOfNotNull(min?.let { "KernelSU ≥ $it" }, max?.let { "KernelSU ≤ $it" }).joinToString(" · ")
    }

    private fun strings(array: org.json.JSONArray?): List<String> =
        (0 until (array?.length() ?: 0).coerceAtMost(64)).mapNotNull { array?.optString(it)?.takeIf(String::isNotBlank) }

    private fun date(seconds: Long): String = if (seconds > 0) {
        runCatching { Instant.ofEpochSecond(seconds).toString().substringBefore('T') }.getOrDefault("")
    } else ""

    private fun httpUrl(value: String): String = value.trim().takeIf {
        it.startsWith("https://") || it.startsWith("http://")
    }.orEmpty()
}
