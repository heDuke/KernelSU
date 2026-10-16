package me.weishu.kernelsu.ui.util

import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import org.json.JSONObject

internal data class RecoveryReadiness(val image: String, val imageSha256: String, val backup: String,
                                      val backupSha1: String, val bytes: Long, val slot: String)

/** Read-only native inspection; the restore backend rechecks the reviewed image hash. */
internal fun inspectRecovery(): RecoveryReadiness {
    check(Natives.isManager && !Natives.isSafeMode && !Natives.requireNewKernel() && rootAvailable()) { "Recovery inspection is unavailable" }
    val daemon = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
    val quoted = "'" + daemon.replace("'", "'\\''") + "'"
    val result = getRootShell().newJob().add("$quoted boot-info recovery-check").exec()
    check(result.isSuccess) { result.err.joinToString("\n").ifBlank { result.out.joinToString("\n") }.take(1200).ifBlank { "Cannot verify matching recovery backup" } }
    val line = result.out.lastOrNull { it.startsWith("{") } ?: error("No recovery report")
    require(line.length <= 8192)
    val json = JSONObject(line)
    return RecoveryReadiness(json.getString("image"), json.getString("image_sha256"), json.getString("backup"),
        json.getString("backup_sha1"), json.getLong("bytes"), json.getString("slot")).also {
        require(it.imageSha256.matches(Regex("[0-9a-f]{64}")) && it.backupSha1.matches(Regex("[0-9a-f]{40}")))
        require(it.bytes in 1..536_870_912 && it.image.length <= 1024 && it.backup.length <= 1024)
    }
}
