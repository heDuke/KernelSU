package me.weishu.kernelsu.data.terminal

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.weishu.kernelsu.ksuApp
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

object TerminalTaskNavigation { val requests = kotlinx.coroutines.flow.MutableStateFlow(0L) }

@Serializable
enum class TaskStatus { QUEUED, STARTING, RUNNING, STOP_REQUESTED, COMPLETED, FAILED, CANCELED, INTERRUPTED;
    val terminal: Boolean get() = when (this) { COMPLETED, FAILED, CANCELED, INTERRUPTED -> true; else -> false }
}
@Serializable
data class TerminalTask(
    val id: String = UUID.randomUUID().toString(), val command: String,
    val name: String = command.lineSequence().first().take(64), val status: TaskStatus = TaskStatus.QUEUED,
    val created: Long = System.currentTimeMillis(), val started: Long = 0, val elapsed: Long = 0, val finished: Long = 0, val startedElapsed: Long = 0,
    val code: Int? = null, val signal: Int? = null, val identity: String = "", val error: String? = null,
    val truncated: Boolean = false,
)

/** All mutable state and file/SQL access are serialized on this repository's monitor. */
object TerminalTaskRepository {
    private val LOG_LIMIT get() = TaskLimitSettings.limits.value.logMiB * 1024 * 1024
    private val TOTAL_LOG_LIMIT get() = TaskLimitSettings.limits.value.totalMiB * 1024L * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val rows = MutableStateFlow<List<TerminalTask>>(emptyList())
    val tasks = rows.asStateFlow()
    private val logs by lazy { File(ksuApp.filesDir, "terminal-tasks").apply { mkdirs() } }
    private val helper by lazy { object : SQLiteOpenHelper(ksuApp, File(ksuApp.noBackupFilesDir, "terminal-tasks.db").path, null, 1) {
        override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE task(id TEXT PRIMARY KEY, created INTEGER NOT NULL, payload TEXT NOT NULL)") }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported task database version") }
    } }
    private var loaded = false
    @Synchronized fun loadHistory() { if (!loaded) restore() }
    private fun readAll(): List<TerminalTask> = helper.readableDatabase.query("task", arrayOf("payload"), null, null, null, null, "created DESC").use { c ->
        buildList { while (c.moveToNext()) add(json.decodeFromString<TerminalTask>(c.getString(0))) }
    }
    private fun put(task: TerminalTask) {
        val values = ContentValues().apply { put("id", task.id); put("created", task.created); put("payload", json.encodeToString(task)) }
        helper.writableDatabase.insertWithOnConflict("task", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    private fun publish() { rows.value = readAll() }
    @Synchronized fun restore() {
        helper.writableDatabase.beginTransaction()
        try {
            readAll().filterNot { it.status.terminal }.forEach { put(it.copy(status = TaskStatus.INTERRUPTED, code = null, error = "Previous controller ended; command was not restarted")) }
            helper.writableDatabase.setTransactionSuccessful()
        } finally { helper.writableDatabase.endTransaction() }
        trim(); publish(); loaded = true
    }
    @Synchronized fun add(task: TerminalTask): Boolean {
        require(UUID.fromString(task.id).toString() == task.id)
        require(task.command.isNotBlank() && task.command.length <= 16384 && '\u0000' !in task.command)
        if (readAll().any { it.id == task.id }) return false
        require(readAll().count { !it.status.terminal } < TaskLimitSettings.limits.value.queued + 1) { "Task queue is full" }
        trim()
        require(logs.listFiles().orEmpty().sumOf { it.length() } + LOG_LIMIT <= TOTAL_LOG_LIMIT) { "Task log quota is full" }
        put(task); publish(); return true
    }
    @Synchronized fun update(id: String, change: (TerminalTask) -> TerminalTask) {
        val old = readAll().firstOrNull { it.id == id } ?: return
        val next = change(old); put(if (next.status.terminal && next.finished == 0L) next.copy(finished = System.currentTimeMillis()) else next); publish()
    }
    @Synchronized fun logFile(id: String): File { require(UUID.fromString(id).toString() == id); return File(logs, "$id.log") }
    @Synchronized fun reserveLog() {
        trim()
        require(logs.listFiles().orEmpty().sumOf { it.length() } + LOG_LIMIT <= TOTAL_LOG_LIMIT) { "Task log quota is full" }
    }
    @Synchronized fun append(id: String, data: ByteArray) {
        val file = logFile(id)
        if (file.length() + data.size > LOG_LIMIT) {
            val tail = RandomAccessFile(file, "r").use { f -> val count = minOf(f.length(), LOG_LIMIT / 2L).toInt(); f.seek(f.length() - count); ByteArray(count).also(f::readFully) }
            file.outputStream().use { it.write("[earlier output truncated]\n".toByteArray()); it.write(tail) }
            update(id) { it.copy(truncated = true) }
        }
        file.appendBytes(data)
    }
    @Synchronized fun tail(id: String): String {
        val file = logFile(id)
        if (!file.isFile) return ""
        return RandomAccessFile(file, "r").use { f -> val count = minOf(f.length(), 64 * 1024L).toInt(); f.seek(f.length() - count); ByteArray(count).also(f::readFully).toString(Charsets.UTF_8) }
    }
    @Synchronized fun remove(id: String) {
        val task = readAll().firstOrNull { it.id == id } ?: return
        require(task.status.terminal) { "Stop the task before deleting it" }
        helper.writableDatabase.delete("task", "id=?", arrayOf(id)); logFile(id).delete(); publish()
    }
    @Synchronized fun applyLimits() { trim(); publish() }
    private fun trim() {
        val all = readAll()
        all.filter { it.status.terminal }.forEachIndexed { index, task ->
            if (index >= TaskLimitSettings.limits.value.records || (task.finished.takeIf { it > 0 } ?: task.created) < System.currentTimeMillis() - TaskLimitSettings.limits.value.days * 86400000L) {
                helper.writableDatabase.delete("task", "id=?", arrayOf(task.id)); logFile(task.id).delete()
            }
        }
        readAll().filter { it.status.terminal }.asReversed().forEach { task ->
            if (logs.listFiles().orEmpty().sumOf { it.length() } + LOG_LIMIT > TOTAL_LOG_LIMIT) {
                logFile(task.id).delete(); put(task.copy(truncated = true))
            }
        }
    }
}
