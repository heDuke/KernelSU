package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AuthorizationTarget

interface SuperUserRepository {
    suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>>
    suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>>
    suspend fun setAuthorization(target: AuthorizationTarget, allowed: Boolean): Result<Unit>
    suspend fun saveProfile(target: AuthorizationTarget, expected: Natives.Profile, updated: Natives.Profile): Result<Unit>
}
