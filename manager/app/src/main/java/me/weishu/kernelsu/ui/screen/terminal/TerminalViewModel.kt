package me.weishu.kernelsu.ui.screen.terminal

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.core.shell.JavaShellProcess
import me.weishu.kernelsu.core.shell.SessionEndReason
import me.weishu.kernelsu.core.shell.ShellProcessHandle
import me.weishu.kernelsu.core.shell.ShellStream
import me.weishu.kernelsu.core.shell.TempShellSession
import me.weishu.kernelsu.core.shell.TerminalBuffer
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.AnsiParser
import me.weishu.kernelsu.ui.util.AnsiSpan
import me.weishu.kernelsu.ui.util.getRootShell
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class TerminalLineKind { STDOUT, STDERR, INPUT, SYSTEM }

@Immutable
data class TerminalLine(val id: Long, val kind: TerminalLineKind, val spans: List<AnsiSpan>) {
    val plain: String get() = spans.joinToString("") { it.text }
}

@Immutable
data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val active: Boolean = false,
    val endReason: SessionEndReason? = null,
    val warningAccepted: Boolean = false,
)

class TerminalViewModel : ViewModel() {
    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val buffer = TerminalBuffer<TerminalLine>()
    private val ids = AtomicLong()
    private val parsers = mapOf(ShellStream.STDOUT to AnsiParser(), ShellStream.STDERR to AnsiParser())
    private val state = MutableStateFlow(TerminalUiState(warningAccepted = prefs.getBoolean(PREF_WARNING_ACCEPTED, false)))
    val uiState: StateFlow<TerminalUiState> = state.asStateFlow()
    private var session: TempShellSession? = null
    @Volatile private var flushScheduled = false

    fun acceptWarning() {
        prefs.edit().putBoolean(PREF_WARNING_ACCEPTED, true).apply()
        state.update { it.copy(warningAccepted = true) }
    }

    /** Starts a session only on explicit user intent (screen opened after the warning). */
    fun startSession() {
        if (session?.isActive == true) return
        if (!Natives.isManager) { appendSystem("Root is not available."); return }
        parsers.values.forEach(AnsiParser::reset)
        clearExports()
        val s = TempShellSession(
            scope = viewModelScope,
            processFactory = ::openRootProcess,
            interrupter = { pid -> getRootShell().newJob().add("pkill -INT -P $pid").exec() },
            onOutput = { stream, text -> append(if (stream == ShellStream.STDERR) TerminalLineKind.STDERR else TerminalLineKind.STDOUT, parsers.getValue(stream).parse(text)) },
            onEnded = { reason ->
                state.update { it.copy(active = false, endReason = reason) }
                appendSystem("[session ended: ${reason.name.lowercase()}]")
            },
        )
        session = s
        state.update { it.copy(active = true, endReason = null) }
        viewModelScope.launch(Dispatchers.IO) { s.start() }
    }

    fun run(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        append(TerminalLineKind.INPUT, listOf(AnsiSpan("# $cmd")))
        if (session?.run(cmd) != true) appendSystem("[no active session]")
    }

    fun interrupt() {
        viewModelScope.launch(Dispatchers.IO) {
            if (session?.interrupt() != true) appendSystem("[interrupt unavailable]")
        }
    }

    fun clear() { synchronized(buffer) { buffer.clear() }; publish() }

    fun onBackground() { session?.onBackground() }
    fun onForeground() { session?.onForeground() }

    fun endSession(reason: SessionEndReason = SessionEndReason.USER) { session?.end(reason) }

    /** Writes the visible scrollback (ANSI stripped) to a cache file for the share sheet. */
    suspend fun exportLog(): File = withContext(Dispatchers.IO) {
        val dir = File(ksuApp.cacheDir, EXPORT_DIR).apply { mkdirs() }
        File(dir, "terminal-${System.currentTimeMillis()}.log").apply {
            writeText(synchronized(buffer) { buffer.snapshot() }.joinToString("\n") { it.plain } + "\n")
        }
    }

    private fun clearExports() { File(ksuApp.cacheDir, EXPORT_DIR).deleteRecursively() }

    private fun appendSystem(text: String) = append(TerminalLineKind.SYSTEM, listOf(AnsiSpan(text)))

    private fun append(kind: TerminalLineKind, spans: List<AnsiSpan>) {
        synchronized(buffer) { buffer.add(TerminalLine(ids.incrementAndGet(), kind, spans)) }
        if (!flushScheduled) {
            flushScheduled = true
            viewModelScope.launch { delay(FLUSH_INTERVAL_MS); flushScheduled = false; publish() }
        }
    }

    private fun publish() {
        val lines = synchronized(buffer) { buffer.snapshot() }
        state.update { it.copy(lines = lines) }
    }

    override fun onCleared() {
        session?.end(SessionEndReason.LEFT_SCREEN)
        clearExports()
    }

    private fun openRootProcess(): ShellProcessHandle {
        val candidates = listOf(listOf(ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so", "debug", "su"), listOf("su"))
        var last: Exception? = null
        for (cmd in candidates) {
            try {
                return JavaShellProcess(ProcessBuilder(cmd).start())
            } catch (e: Exception) { last = e; Log.w(TAG, "temp shell: ${cmd.first()} failed", e) }
        }
        throw last ?: IllegalStateException("No root shell")
    }

    companion object {
        private const val TAG = "TempTerminal"
        const val PREF_WARNING_ACCEPTED = "temp_terminal_warning_accepted"
        private const val EXPORT_DIR = "terminal-export"
        private const val FLUSH_INTERVAL_MS = 50L
    }
}
