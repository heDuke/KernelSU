package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.RepoModule

interface ModuleRepoRepository {
    val sources: kotlinx.coroutines.flow.StateFlow<List<me.weishu.kernelsu.data.model.RepoSourceStatus>>
    fun setSourceEnabled(id: String, enabled: Boolean)
    suspend fun fetchModules(): Result<List<RepoModule>>
}
