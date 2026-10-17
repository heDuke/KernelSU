package me.weishu.kernelsu.data.terminal

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.weishu.kernelsu.ksuApp
import java.io.File
import java.util.UUID

@Serializable
data class CommandFavorite(
    val id: String = UUID.randomUUID().toString(), val name: String, val command: String,
    val group: String = "", val pinned: Boolean = false,
)

@Serializable
data class FavoritesDocument(val schema: Int = 1, val commands: List<CommandFavorite> = emptyList())

object FavoritePolicy {
    const val MAX_COMMAND_CHARS = 16384
    private val json = Json { ignoreUnknownKeys = true }
    fun validate(commands: List<CommandFavorite>) {
        require(commands.size <= 200) { "At most 200 favorites" }
        require(commands.map { it.id }.distinct().size == commands.size) { "Duplicate favorite identity" }
        require(commands.count { it.pinned } <= 4) { "At most four pinned commands" }
        require(commands.map { it.group }.filter { it.isNotBlank() }.distinct().size <= 20) { "At most 20 groups" }
        commands.forEach {
            require(runCatching { UUID.fromString(it.id).toString() == it.id }.getOrDefault(false)) { "Invalid favorite identity" }
            require(it.name.isNotBlank() && it.name.length <= 64) { "Name must contain 1-64 characters" }
            require(it.group.length <= 64) { "Group is too long" }
            require(it.command.isNotBlank() && it.command.length <= MAX_COMMAND_CHARS && '\u0000' !in it.command) { "Command is blank or too long" }
        }
    }
    fun encode(commands: List<CommandFavorite>): String { validate(commands); return json.encodeToString(FavoritesDocument(commands = commands)).also { require(it.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) { "Favorites exceed storage limit" } } }
    fun decode(text: String): List<CommandFavorite> {
        require(text.length <= 4 * 1024 * 1024) { "Favorites exceed storage limit" }
        val document = json.decodeFromString<FavoritesDocument>(text)
        require(document.schema == 1) { "Unsupported favorites format" }
        validate(document.commands)
        return document.commands
    }
}

/** One process-wide writer; favorites are never submitted to a shell by this repository. */
object CommandFavorites {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<CommandFavorite>>(emptyList())
    val commands = mutable.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val store by lazy { AtomicFile(File(ksuApp.noBackupFilesDir, "terminal-favorites.json")) }
    private var loaded = false
    init { scope.launch { runCatching { mutex.withLock { load() } }.onFailure { mutableError.value = it.message } } }
    private fun load() {
        if (loaded) return
        try {
            if (store.baseFile.exists() || File(store.baseFile.path + ".bak").exists()) {
                val bytes = store.openRead().use { it.readNBytes(4 * 1024 * 1024 + 1) }
                require(bytes.size <= 4 * 1024 * 1024)
                mutable.value = FavoritePolicy.decode(bytes.toString(Charsets.UTF_8))
            }
            loaded = true
        } catch (e: Exception) { mutableError.value = e.message; throw e }
    }
    private fun change(transform: (List<CommandFavorite>) -> List<CommandFavorite>) {
        scope.launch {
            runCatching { mutex.withLock {
                load()
                val next = transform(mutable.value)
                val bytes = FavoritePolicy.encode(next).toByteArray(Charsets.UTF_8)
                val output = store.startWrite()
                try { output.write(bytes); store.finishWrite(output) }
                catch (e: Exception) { store.failWrite(output); throw e }
                mutable.value = next; mutableError.value = null
            } }.onFailure { mutableError.value = it.message ?: "Cannot save favorite" }
        }
    }
    fun save(command: CommandFavorite) = change { list ->
        val position = list.indexOfFirst { it.id == command.id }
        if (position < 0) list + command else list.toMutableList().apply { set(position, command) }
    }
    fun remove(id: String) = change { it.filterNot { command -> command.id == id } }
    fun move(id: String, delta: Int) = change { list ->
        val at = list.indexOfFirst { it.id == id }
        if (at < 0) list else list.toMutableList().apply { val item = removeAt(at); add((at + delta).coerceIn(0, size), item) }
    }
}
