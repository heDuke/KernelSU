package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.EnvCheckReport

interface EnvCheckRepository {
    suspend fun runCheck(): Result<EnvCheckReport>
}
