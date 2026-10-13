package me.weishu.kernelsu.data.model

data class RepoSource(val id: String, val name: String, val indexUrl: String)
data class RepoSourceStatus(
    val source: RepoSource,
    val enabled: Boolean = true,
    val count: Int = 0,
    val cached: Boolean = false,
    val lastSync: Long = 0,
    val error: String? = null,
)
