package me.weishu.kernelsu.core.utils

import java.io.File
import java.io.IOException

fun reserveDownloadTarget(directory: File, requestedName: String): File {
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create download directory")
    val name = requestedName.substringAfterLast('/').substringAfterLast('\\')
        .map { if (it.code < 32 || it.code == 127) '_' else it }.joinToString("").take(80)
        .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "module.zip"
    val dot = name.lastIndexOf('.').takeIf { it > 0 }
    val base = dot?.let { name.substring(0, it) } ?: name
    val extension = dot?.let { name.substring(it) } ?: ""
    repeat(10000) { index ->
        val candidate = File(directory, if (index == 0) name else "$base ($index)$extension")
        if (candidate.createNewFile()) return candidate
    }
    throw IOException("Too many download filename collisions")
}
