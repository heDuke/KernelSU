package me.weishu.kernelsu.ui.screen.install

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.core.utils.BootInputInspection
import me.weishu.kernelsu.core.utils.inspectBootInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

internal data class StagedBootInput(val uri: Uri, val name: String, val bytes: Long, val sha256: String, val inspection: BootInputInspection)

/** Freeze SAF input before review, so execution consumes the exact reviewed bytes. */
internal suspend fun stageBootInput(context: Context, uri: Uri, module: Boolean): StagedBootInput {
    var allocated: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "boot-review").apply { check(isDirectory || mkdirs()) }
            // Only aged files created by this staging directory are eligible for cleanup.
            directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 7 * 86_400_000L }?.forEach { it.delete() }
            val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).filterNot(Char::isISOControl).take(160) else null
            } }.getOrNull() ?: "input"
            val file = File.createTempFile("input-", if (module) ".ko" else ".img", directory)
            allocated = file
            try {
                val max = minOf(if (module) 16L * 1024 * 1024 else 512L * 1024 * 1024, (directory.usableSpace - 32L * 1024 * 1024).coerceAtLeast(0))
                val prefix = ByteArrayOutputStream()
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    file.outputStream().use { output ->
                        val bytes = ByteArray(16_384)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(bytes); if (count < 0) break
                            size += count; require(size <= max) { "Input exceeds size limit" }
                            digest.update(bytes, 0, count); output.write(bytes, 0, count)
                            val remaining = (if (module) 16 * 1024 * 1024 else 65536) - prefix.size()
                            if (remaining > 0) prefix.write(bytes, 0, minOf(remaining, count))
                        }
                        output.fd.sync()
                    }
                }
                var inspection = inspectBootInput(prefix.toByteArray(), size)
                if (!module) {
                    val target = runCatching { me.weishu.kernelsu.core.tasks.BootKernelVersion.parseTargetFromBoot(file) }.getOrNull()
                    if (target != null) inspection = inspection.copy(release = target.release, kmi = target.kmi, kernelPageSize = target.pageSizeBytes)
                }
                require(!module || inspection.format == "ELF64 ARM64 LKM") { "Expected an ARM64 module" }
                require(module || inspection.format != "ELF64 ARM64 LKM") { "Expected a boot image" }
                StagedBootInput(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file), name, size,
                    digest.digest().joinToString("") { "%02x".format(it) }, inspection)
            } catch (error: Exception) { file.delete(); throw error }
        }
    } catch (failure: Exception) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { allocated?.delete() }
        throw failure
    }
}

@kotlinx.parcelize.Parcelize
internal data class InstallReviewSnapshot(
    val operation: me.weishu.kernelsu.ui.screen.flash.FlashIt,
    val report: String,
    val inputs: Map<String, String>,
    val allowed: Boolean,
    val checkOnly: Boolean,
    val expectedSlot: String?,
    val expectedRelease: String,
    val expectedFingerprint: String,
    val summary: Map<String, String> = emptyMap(),
) : android.os.Parcelable

internal suspend fun verifyReviewedInputs(context: Context, inputs: Map<String, String>) = withContext(Dispatchers.IO) {
    inputs.forEach { (value, expected) ->
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        requireNotNull(context.contentResolver.openInputStream(Uri.parse(value))).use { input ->
            val buffer = ByteArray(16384)
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                total += count; require(total <= 536_870_912)
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Reviewed file changed or expired; check again" }
    }
}

internal suspend fun releaseReviewedInputs(context: Context, inputs: Map<String, String>) = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
    inputs.keys.forEach { value ->
        me.weishu.kernelsu.ui.util.reviewedBootFile(Uri.parse(value))?.delete()
    }
}
