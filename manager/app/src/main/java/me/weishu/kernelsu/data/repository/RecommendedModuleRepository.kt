package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.RecommendedModule

interface RecommendedModuleRepository {
    suspend fun getRecommendedModules(): Result<List<RecommendedModule>>
}
