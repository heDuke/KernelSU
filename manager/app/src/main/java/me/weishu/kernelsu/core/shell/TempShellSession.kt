package me.weishu.kernelsu.core.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal view of a running shell process, so tests can use a fake. */
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

/**
 * One temporary root shell: a single `su` process for the lifetime of the session.
 * Nothing is persisted; output is only delivered to [onOutput].
 *
 * Interrupt: the session has no TTY, so Ctrl-C cannot be sent through stdin. The shell
 * reports its PID at start-up, and [interrupter] sends SIGINT to that shell's children
 * (the running command) from a separate root context.
 */
class TempShellSession(
    private val scope: CoroutineScope,
    private val processFactory: () -> ShellProcessHandle,
    private val interrupter: (shellPid: Int) -> Unit,
    private val onOutput: (ShellStream, String) -> Unit,
    private val onEnded: (SessionEndReason) -> Unit,
    private val backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var process: ShellProcessHandle? = null
    private var timeoutJob: Job? = null
    private val ended = AtomicBoolean(false)

    @Volatile var shellPid: Int? = null
        private set

    val isActive: Boolean get() = process != null && !ended.get()

    fun start() {
        check(process == null) { "Session already started" }
        val handle = try { processFactory() } catch (e: Exception) {
            onOutput(ShellStream.STDERR, e.message ?: e.javaClass.simpleName)
            end(SessionEndReason.START_FAILED); return
        }
        process = handle
        var finishedStreams = 0
        val lock = Any()
        fun streamDone() { if (synchronized(lock) { ++finishedStreams } == 2) end(SessionEndReason.PROCESS_EXITED) }
        scope.launch(ioDispatcher) { pump(handle.stdout, ShellStream.STDOUT); streamDone() }
        scope.launch(ioDispatcher) { pump(handle.stderr, ShellStream.STDERR); streamDone() }
        write("echo $PID_MARKER$$")
    }

    private fun pump(input: InputStream, stream: ShellStream) {
        try {
            input.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (stream == ShellStream.STDOUT && line.startsWith(PID_MARKER) && shellPid == null) {
                        shellPid = line.removePrefix(PID_MARKER).trim().toIntOrNull()
                        continue
                    }
                    if (!ended.get()) onOutput(stream, line)
                }
            }
        } catch (_: IOException) { }
    }

    /** Sends one command line to the shell. Returns false if the session is not running. */
    fun run(command: String): Boolean {
        if (command.isBlank() || !isActive) return false
        return write(command)
    }

    private fun write(line: String): Boolean = try {
        process?.stdin?.let { it.write((line + "\n").toByteArray()); it.flush(); true } ?: false
    } catch (_: IOException) { false }

    /** Interrupts the command currently running in the shell (the shell itself stays alive). */
    fun interrupt(): Boolean {
        val pid = shellPid ?: return false
        if (!isActive) return false
        return try { interrupter(pid); true } catch (_: Exception) { false }
    }

    fun onBackground() {
        if (!isActive) return
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(backgroundTimeoutMs)
            end(SessionEndReason.BACKGROUND_TIMEOUT)
        }
    }

    fun onForeground() { timeoutJob?.cancel(); timeoutJob = null }

    fun end(reason: SessionEndReason) {
        if (!ended.compareAndSet(false, true)) return
        timeoutJob?.cancel()
        process?.let { p ->
            try { p.stdin.close() } catch (_: IOException) { }
            p.destroy()
        }
        onEnded(reason)
    }

    companion object {
        const val DEFAULT_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
        const val PID_MARKER = "__HUSKY_TEMP_SHELL_PID__"
    }
}

/** In-memory scrollback that keeps only the newest [maxLines] lines. */
class TerminalBuffer<T>(private val maxLines: Int = DEFAULT_MAX_LINES) {
    private val lines = ArrayDeque<T>()
    val size: Int get() = lines.size
    fun add(line: T) { lines.addLast(line); while (lines.size > maxLines) lines.removeFirst() }
    fun clear() = lines.clear()
    fun snapshot(): List<T> = lines.toList()

    companion object { const val DEFAULT_MAX_LINES = 5000 }
}
