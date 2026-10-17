package me.weishu.kernelsu.data.terminal

import me.weishu.kernelsu.ksuApp
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class TaskExit(val code: Int, val signal: Int)
fun classifyTaskExit(exit: TaskExit, stopRequested: Boolean): TaskStatus = when {
    exit.code < 0 && exit.signal == 0 -> TaskStatus.INTERRUPTED
    stopRequested && exit.signal != 0 -> TaskStatus.CANCELED
    exit.code == 0 && exit.signal == 0 -> TaskStatus.COMPLETED
    else -> TaskStatus.FAILED
}

/** Framing is produced by the native supervisor, never parsed from command output. */
class TaskWorkerClient {
    private val lock = Any()
    private var input: DataOutputStream? = null
    @Volatile private var process: Process? = null
    private var ready = false
    val stopRequested = AtomicBoolean(false)
    fun stop() { stopRequested.set(true); synchronized(lock) { if (ready) runCatching { input?.writeByte('T'.code); input?.flush() } } }
    fun interrupt() { synchronized(lock) { if (ready) runCatching { input?.writeByte('I'.code); input?.flush() } } }
    fun execute(command: String, onStarted: (String) -> Unit, onOutput: (ByteArray) -> Unit): TaskExit {
        require(command.isNotBlank() && command.length <= 16384 && '\u0000' !in command)
        val path = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
        val child = ProcessBuilder(path, "debug", "su").start()
        process = child
        Thread({ runCatching { child.errorStream.use { stream -> val b = ByteArray(2048); while (stream.read(b) >= 0) { /* Bootstrap diagnostics cannot be task-control records. */ } } } }, "task-bootstrap-stderr").apply { isDaemon = true; start() }
        val timeout = Executors.newSingleThreadScheduledExecutor()
        val deadline = timeout.schedule({ closeInput(); child.destroy() }, 10, TimeUnit.SECONDS)
        try {
            val outgoing = DataOutputStream(child.outputStream)
            synchronized(lock) { input = outgoing }
            val escaped = "'" + path.replace("'", "'\\''") + "'"
            outgoing.write("exec $escaped debug task-runner\n".toByteArray()); outgoing.flush()
            val incoming = DataInputStream(child.inputStream)
            val hello = readFrame(incoming)
            check(hello.first == 'H'.code) { "Task worker handshake failed" }
            val identity = hello.second.toString(Charsets.US_ASCII).split(':')
            check(identity.size == 3 && identity[0] == "0" && (identity[1].toLongOrNull() ?: 0) > 1 && (identity[2].toLongOrNull() ?: 0) > 0) { "Task worker Root identity missing" }
            deadline.cancel(false)
            synchronized(lock) {
                val bytes = command.toByteArray(Charsets.UTF_8)
                outgoing.writeInt(bytes.size); outgoing.write(bytes); outgoing.flush()
                ready = true
                if (stopRequested.get()) { outgoing.writeByte('T'.code); outgoing.flush() }
            }
            while (true) {
                val (tag, payload) = readFrame(incoming)
                when (tag) {
                    'R'.code -> onStarted(payload.toString(Charsets.US_ASCII))
                    'O'.code, 'E'.code -> onOutput(payload)
                    'X'.code -> {
                        val fields = payload.toString(Charsets.US_ASCII).split(':')
                        check(fields.size == 2)
                        return TaskExit(fields[0].toInt(), fields[1].toInt())
                    }
                    else -> throw IOException("Invalid worker control record")
                }
            }
        } finally { deadline.cancel(false); timeout.shutdownNow() }
    }
    private fun readFrame(stream: DataInputStream): Pair<Int, ByteArray> {
        val tag = stream.readUnsignedByte(); val size = stream.readInt()
        require(size in 0..65536) { "Worker frame exceeds limit" }
        return tag to ByteArray(size).also { stream.readFully(it) }
    }
    private fun closeInput() { synchronized(lock) { ready = false; runCatching { input?.close() }; input = null } }
    /** Returns observed worker termination; a timeout is not reported as success. */
    fun closeAndWait(): Boolean {
        closeInput()
        val child = process ?: return true
        if (child.waitFor(7, TimeUnit.SECONDS)) return true
        child.destroy()
        return child.waitFor(2, TimeUnit.SECONDS)
    }
    fun isAlive() = process?.isAlive == true
}
