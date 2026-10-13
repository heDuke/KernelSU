package me.weishu.kernelsu.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

object DownloadManager {

    enum class Status { PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELED }

    data class DownloadState(
        val id: Int,
        val fileName: String,
        val url: String,
        val progress: Int = 0,
        val status: Status = Status.PENDING,
        val resultUri: Uri? = null,
        val error: String? = null,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1,
        val bytesPerSecond: Long = 0,
    )

    private val idCounter = AtomicInteger(0)
    private val _downloads = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Int, DownloadState>> = _downloads.asStateFlow()


    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
    ): Int {
        val existing = _downloads.value.values.find {
            it.url == url && (it.status == Status.PENDING || it.status == Status.DOWNLOADING)
        }
        if (existing != null) return existing.id

        val id = idCounter.incrementAndGet()
        val state = DownloadState(id = id, fileName = fileName, url = url)
        _downloads.update { it + (id to state) }


        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
        }
        try { ContextCompat.startForegroundService(context, intent) }
        catch (error: Exception) {
            markFailed(id, error.message ?: "Cannot start download service")
            throw error
        }
        return id
    }

    internal fun updateProgress(id: Int, progress: Int, downloadedBytes: Long = 0, totalBytes: Long = -1, bytesPerSecond: Long = 0) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(progress = progress, status = Status.DOWNLOADING,
                downloadedBytes = downloadedBytes, totalBytes = totalBytes, bytesPerSecond = bytesPerSecond))
        }
    }

    internal fun markCompleted(id: Int, uri: Uri) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = Status.COMPLETED, progress = 100, resultUri = uri))
        }

    }

    internal fun markCanceled(id: Int) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = Status.CANCELED, error = "Cancelled"))
        }
    }

    internal fun markFailed(id: Int, error: String) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = Status.FAILED, error = error))
        }
    }
}
