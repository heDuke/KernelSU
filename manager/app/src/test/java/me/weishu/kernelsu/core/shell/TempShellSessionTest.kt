package me.weishu.kernelsu.core.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fake shell: lines written to stdin are echoed (PID marker answered with 4242). */
private class FakeShell : ShellProcessHandle {
    val out = PipedOutputStream(); val err = PipedOutputStream()
    override val stdout = PipedInputStream(out); override val stderr = PipedInputStream(err)
    val commands = CopyOnWriteArrayList<String>()
    @Volatile var destroyed = false
    private val pending = StringBuilder()
    override val stdin = object : java.io.OutputStream() {
        override fun write(b: Int) {
            if (b == '\n'.code) {
                val cmd = pending.toString(); pending.clear(); commands += cmd
                if (cmd.startsWith("echo ${TempShellSession.PID_MARKER}")) emit("${TempShellSession.PID_MARKER}4242")
                else if (cmd.startsWith("err ")) { err.write((cmd.removePrefix("err ") + "\n").toByteArray()); err.flush() }
                else emit(cmd)
            } else pending.append(b.toChar())
        }
    }
    fun emit(line: String) { out.write((line + "\n").toByteArray()); out.flush() }
    override fun destroy() { destroyed = true; runCatching { out.close() }; runCatching { err.close() } }
}

class TempShellSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() = scope.cancel()

    private fun waitFor(timeoutMs: Long = 3000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) { check(System.currentTimeMillis() < end) { "timed out" }; Thread.sleep(10) }
    }

    @Test fun streamsStdoutAndStderrAndHidesPidMarker() {
        val shell = FakeShell(); val out = CopyOnWriteArrayList<Pair<ShellStream, String>>()
        val s = TempShellSession(scope, { shell }, {}, { st, l -> out += st to l }, {})
        s.start(); waitFor { s.shellPid == 4242 }
        s.run("hello"); s.run("err boom")
        waitFor { out.size == 2 }
        assertTrue(ShellStream.STDOUT to "hello" in out)
        assertTrue(ShellStream.STDERR to "boom" in out)
        assertFalse(out.any { it.second.contains(TempShellSession.PID_MARKER) })
        s.end(SessionEndReason.USER)
    }

    @Test fun backgroundTimeoutEndsSessionAndForegroundCancels() {
        val shell = FakeShell(); val reasons = CopyOnWriteArrayList<SessionEndReason>()
        val ended = CountDownLatch(1)
        val s = TempShellSession(scope, { shell }, {}, { _, _ -> }, { reasons += it; ended.countDown() }, backgroundTimeoutMs = 150)
        s.start()
        s.onBackground(); Thread.sleep(50); s.onForeground(); Thread.sleep(250)
        assertTrue("foreground must cancel the timer", s.isActive)
        s.onBackground()
        assertTrue(ended.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(SessionEndReason.BACKGROUND_TIMEOUT), reasons)
        assertTrue(shell.destroyed); assertFalse(s.isActive)
        assertFalse(s.run("after"))
    }

    @Test fun defaultTimeoutIsFiveMinutes() = assertEquals(300_000L, TempShellSession.DEFAULT_BACKGROUND_TIMEOUT_MS)

    @Test fun interruptTargetsShellPidOnlyWhileActive() {
        val shell = FakeShell(); val pids = CopyOnWriteArrayList<Int>()
        val s = TempShellSession(scope, { shell }, { pids += it }, { _, _ -> }, {})
        assertFalse(s.interrupt())
        s.start(); waitFor { s.shellPid != null }
        assertTrue(s.interrupt()); assertEquals(listOf(4242), pids)
        assertTrue("shell survives interrupt", s.isActive)
        s.end(SessionEndReason.USER)
        assertFalse(s.interrupt()); assertEquals(1, pids.size)
    }

    @Test fun endIsIdempotentAndProcessExitIsReported() {
        val shell = FakeShell(); val reasons = CopyOnWriteArrayList<SessionEndReason>()
        val s = TempShellSession(scope, { shell }, {}, { _, _ -> }, { reasons += it })
        s.start(); shell.destroy()
        waitFor { reasons.isNotEmpty() }
        s.end(SessionEndReason.USER)
        assertEquals(listOf(SessionEndReason.PROCESS_EXITED), reasons)
    }

    @Test fun startFailureIsReported() {
        val reasons = CopyOnWriteArrayList<SessionEndReason>()
        val s = TempShellSession(scope, { error("no su") }, {}, { _, _ -> }, { reasons += it })
        s.start()
        assertEquals(listOf(SessionEndReason.START_FAILED), reasons)
    }

    @Test fun bufferKeepsNewestLinesUpToCap() {
        val buf = TerminalBuffer<Int>()
        repeat(5003) { buf.add(it) }
        assertEquals(5000, buf.size)
        assertEquals(3, buf.snapshot().first()); assertEquals(5002, buf.snapshot().last())
        buf.clear(); assertEquals(0, buf.size)
        assertEquals(5000, TerminalBuffer.DEFAULT_MAX_LINES)
    }
}
