package me.weishu.kernelsu.core.utils

/** Exact, bounded names only; never forward unvalidated input to a shell argument. */
fun parseBootModuleBlocklist(text: String): List<String> {
    require(text.length <= 4096)
    if (text.isBlank()) return emptyList()
    val names = text.split(',').map { it.trim() }
    require(names.size <= 64 && names.all { it.matches(Regex("[A-Za-z0-9_]{1,64}")) })
    return names.distinct()
}
