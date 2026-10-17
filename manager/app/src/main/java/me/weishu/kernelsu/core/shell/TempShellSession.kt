package me.weishu.kernelsu.core.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface ShellProcessHandle {
    val stdin: OutputStream
    val stdout: InputStream
    val stderr: InputStream
    fun destroy()
}

class JavaShellProcess(private val process: Process) : ShellProcessHandle {
    override val stdin: OutputStream get() = process.outputStream
    override val stdout: InputStream get() = process.inputStream
    override val stderr: InputStream get() = process.errorStream
    override fun destroy() { process.destroy() }
}

enum class ShellStream { STDOUT, STDERR }
enum class SessionEndReason { USER, LEFT_SCREEN, BACKGROUND_TIMEOUT, PROCESS_EXITED, START_FAILED }
data class ShellIdentity(val pid: Int, val processGroup: Int = 0, val startTicks: Long = 0)

/** A bounded, line-oriented shell, not a PTY. Lifecycle transitions share one lock. */
class TempShellSession(
    private val scope: CoroutineScope,
    private val processFactory: () -> ShellProcessHandle,
    private val interrupter: (shellPid: Int) -> Unit,
    private val onOutput: (ShellStream, String) -> Unit,
    private val onEnded: (SessionEndReason) -> Unit,
    private val backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val requireRootIdentity: Boolean = false,
    private val marker: String = PID_MARKER,
    private val bootstrap: (String) -> String = { "echo $it$$" },
    private val terminator: (ShellIdentity) -> Unit = {},
    private val onReady: () -> Unit = {},
    private val readyTimeoutMs: Long = 10_000,
) {
    private val lock = Any()
    private val writeLock = Any()
    private var process: ShellProcessHandle? = null
    private var started = false
    private var ended = false
    private var identity: ShellIdentity? = null
    private var timeoutJob: Job? = null
    private var readyJob: Job? = null
    private var backgroundGeneration = 0L
    val shellPid: Int? get() = synchronized(lock) { identity?.pid }
    val isActive: Boolean get() = synchronized(lock) { process != null && !ended }
    val isRunning: Boolean get() = synchronized(lock) { started && !ended }
    val shellIdentity: ShellIdentity? get() = synchronized(lock) { identity }

    fun start() {
        synchronized(lock) {
            if (ended || started) return
            started = true
        }
        val handle = try { processFactory() } catch (error: Exception) {
            onOutput(ShellStream.STDERR, error.message ?: error.javaClass.simpleName)
            end(SessionEndReason.START_FAILED)
            return
        }
        val accepted = synchronized(lock) {
            if (ended) false else { process = handle; true }
        }
        if (!accepted) { close(handle); return }
        var finishedStreams = 0
        val streamLock = Any()
        fun streamDone() {
            if (synchronized(streamLock) { ++finishedStreams } == 2) end(SessionEndReason.PROCESS_EXITED)
        }
        scope.launch(ioDispatcher) { try { pump(handle.stdout, ShellStream.STDOUT) } finally { streamDone() } }
        scope.launch(ioDispatcher) { try { pump(handle.stderr, ShellStream.STDERR) } finally { streamDone() } }
        synchronized(lock) {
            if (!ended) readyJob = scope.launch {
                delay(readyTimeoutMs)
                if (synchronized(lock) { !ended && identity == null }) end(SessionEndReason.START_FAILED)
            }
        }
        if (!write(bootstrap(marker))) end(SessionEndReason.START_FAILED)
    }

    private fun receive(stream: ShellStream, text: String) {
        if (stream == ShellStream.STDOUT && text.startsWith(marker)) {
            val parts = text.removePrefix(marker).split(':')
            val pid = parts.firstOrNull()?.toIntOrNull()
            val group = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val ticks = parts.getOrNull(3)?.toLongOrNull() ?: 0
            if (pid == null || pid <= 1 || (requireRootIdentity &&
                    (parts.size != 4 || parts[1] != "0" || group != pid || ticks <= 0))) {
                end(SessionEndReason.START_FAILED)
                return
            }
            synchronized(lock) {
                if (identity == null && !ended) {
                    identity = ShellIdentity(pid, group, ticks)
                    readyJob?.cancel()
                    onReady()
                }
            }
            return
        }
        if (synchronized(lock) { !ended }) onOutput(stream, text)
    }

    private fun pump(input: InputStream, stream: ShellStream) {
        try {
            input.bufferedReader().use { reader ->
                val chars = CharArray(2048)
                val line = StringBuilder()
                var previousCr = false
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    for (index in 0 until count) {
                        val char = chars[index]
                        if (char == '\r' || char == '\n') {
                            if (!(char == '\n' && previousCr)) { receive(stream, line.toString()); line.clear() }
                            previousCr = char == '\r'
                        } else {
                            previousCr = false
                            line.append(char)
                            if (line.length >= MAX_LINE_CHARS) { receive(stream, line.toString()); line.clear() }
                        }
                    }
                }
                if (line.isNotEmpty()) receive(stream, line.toString())
            }
        } catch (_: IOException) { }
    }

    fun run(command: String): Boolean {
        if (command.isBlank() || command.length > MAX_COMMAND_CHARS ||
            synchronized(lock) { identity == null || ended }) return false
        return write(command)
    }

    private fun write(line: String): Boolean = synchronized(writeLock) {
        val handle = synchronized(lock) { process.takeUnless { ended } } ?: return false
        try { handle.stdin.write((line + "\n").toByteArray()); handle.stdin.flush(); true }
        catch (_: IOException) { false }
    }

    fun interrupt(): Boolean {
        val pid = synchronized(lock) { identity?.pid.takeUnless { ended } } ?: return false
        return try { interrupter(pid); true } catch (_: Exception) { false }
    }

    fun onBackground() = synchronized(lock) {
        if (ended) return@synchronized
        timeoutJob?.cancel()
        val generation = ++backgroundGeneration
        timeoutJob = scope.launch {
            delay(backgroundTimeoutMs)
            end(SessionEndReason.BACKGROUND_TIMEOUT, generation)
        }
    }

    fun onForeground() = synchronized(lock) {
        backgroundGeneration++
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun end(reason: SessionEndReason, expectedBackgroundGeneration: Long? = null) {
        val owned = synchronized(lock) {
            if (ended || (expectedBackgroundGeneration != null && backgroundGeneration != expectedBackgroundGeneration)) return
            ended = true
            timeoutJob?.cancel()
            readyJob?.cancel()
            process to identity
        }
        val handle = owned.first
        val owner = owned.second
        if (handle != null) {
            if (requireRootIdentity && owner != null && reason != SessionEndReason.PROCESS_EXITED) {
                // Cleanup survives ViewModel cancellation and never blocks the UI.
                scope.launch(NonCancellable + ioDispatcher) {
                    try { terminator(owner) }
                    catch (error: Exception) { onOutput(ShellStream.STDERR, "Session cleanup failed: ${error.message}") }
                    finally { close(handle) }
                }
            } else close(handle)
        }
        onEnded(reason)
    }

    private fun close(handle: ShellProcessHandle) {
        runCatching { handle.destroy() }
        runCatching { handle.stdin.close() }
        runCatching { handle.stdout.close() }
        runCatching { handle.stderr.close() }
    }

    companion object {
        const val DEFAULT_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
        const val PID_MARKER = "__HUSKY_TEMP_SHELL_PID__"
        const val MAX_LINE_CHARS = 8192
        const val MAX_COMMAND_CHARS = 16384
    }
}

/** Both line count and character count are bounded, including echoed input. */
class TerminalBuffer<T>(
    private val maxLines: Int = DEFAULT_MAX_LINES,
    private val maxChars: Int = 512 * 1024,
    private val length: (T) -> Int = { 1 },
) {
    init { require(maxLines > 0 && maxChars > 0) }
    private val lines = ArrayDeque<T>()
    private var chars = 0L
    val size: Int get() = lines.size
    fun add(line: T) {
        lines.addLast(line)
        chars += length(line).coerceAtLeast(0)
        while (lines.size > maxLines || chars > maxChars) chars -= length(lines.removeFirst()).coerceAtLeast(0)
    }
    fun clear() { lines.clear(); chars = 0 }
    fun snapshot(): List<T> = lines.toList()
    companion object { const val DEFAULT_MAX_LINES = 5000 }
}
