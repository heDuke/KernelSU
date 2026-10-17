package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.RepoModule

interface ModuleRepoRepository {
    suspend fun fetchDetail(moduleId: String): Result<me.weishu.kernelsu.data.model.RepoModuleDetail> = OfficialRegistryRepository().fetchDetail(moduleId)
    suspend fun fetchChangelogHtml(moduleId: String, tag: String?): String? = OfficialRegistryRepository().fetchChangelogHtml(moduleId, tag)
    suspend fun resolveUpdate(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Int): me.weishu.kernelsu.data.registry.UpdateVerification = OfficialRegistryRepository().resolveUpdate(moduleId, updateJsonUrl, zipUrl, versionCode)
    fun isRegistryUrl(url: String): Boolean = OfficialRegistryRepository().isRegistryUrl(url)
    suspend fun fetchRegistryText(url: String): String? = OfficialRegistryRepository().fetchRegistryText(url)
    val sources: kotlinx.coroutines.flow.StateFlow<List<me.weishu.kernelsu.data.model.RepoSourceStatus>>
    fun setSourceEnabled(id: String, enabled: Boolean)
    suspend fun fetchModules(): Result<List<RepoModule>>
}
