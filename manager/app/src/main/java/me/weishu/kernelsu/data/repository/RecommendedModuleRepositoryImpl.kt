package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.RecommendedModule
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray

class RecommendedModuleRepositoryImpl : RecommendedModuleRepository {

    companion object {
        private const val ASSET = "recommended_modules.json"
    }

    override suspend fun getRecommendedModules(): Result<List<RecommendedModule>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = ksuApp.assets.open(ASSET).bufferedReader().use { it.readText() }
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id", "")
                    if (id.isEmpty()) return@mapNotNull null
                    RecommendedModule(
                        id = id,
                        name = item.optString("name", id),
                        description = item.optString("description", ""),
                        downloadUrl = item.optString("downloadUrl", ""),
                        homepage = item.optString("homepage", ""),
                        note = item.optString("note", ""),
                    )
                }
            }
        }
}