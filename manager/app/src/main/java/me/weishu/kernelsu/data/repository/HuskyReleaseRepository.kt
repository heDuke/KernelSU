package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.HuskyRelease
import java.io.File

interface HuskyReleaseRepository {
    suspend fun fetchLatest(): Result<HuskyRelease>
    suspend fun downloadToFile(url: String, dest: File): Result<File>
}
