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
private class FakeShell(private val readiness: String = "4242") : ShellProcessHandle {
    val out = PipedOutputStream(); val err = PipedOutputStream()
    override val stdout = PipedInputStream(out); override val stderr = PipedInputStream(err)
    val commands = CopyOnWriteArrayList<String>()
    @Volatile var destroyed = false
    private val pending = StringBuilder()
    override val stdin = object : java.io.OutputStream() {
        override fun write(b: Int) {
            if (b == '\n'.code) {
                val cmd = pending.toString(); pending.clear(); commands += cmd
                if (cmd.startsWith("echo ${TempShellSession.PID_MARKER}")) emit("${TempShellSession.PID_MARKER}$readiness")
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
    @Test fun failedInterruptIsNotReportedAsSuccess() {
        val shell = FakeShell()
        val session = TempShellSession(scope, { shell }, { error("signal failed") }, { _, _ -> }, {})
        session.start(); waitFor { session.shellPid != null }
        assertFalse(session.interrupt())
        session.end(SessionEndReason.USER)
    }

    @Test fun scrollbackAlsoBoundsCharacters() {
        val buffer = TerminalBuffer<String>(maxLines = 50, maxChars = 10, length = String::length)
        buffer.add("123456"); buffer.add("abcdef")
        assertEquals(listOf("abcdef"), buffer.snapshot())
        buffer.add("x".repeat(11))
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test fun rejectsNonRootReadinessAndDoesNotAcceptCommandsBeforeReady() {
        val shell = FakeShell()
        val reasons = CopyOnWriteArrayList<SessionEndReason>()
        val session = TempShellSession(scope, { shell }, {}, { _, _ -> }, { reasons += it },
            requireRootIdentity = true)
        assertFalse(session.run("must not run"))
        session.start()
        waitFor { reasons.isNotEmpty() }
        assertEquals(listOf(SessionEndReason.START_FAILED), reasons)
        assertFalse(session.run("must not run"))
    }

    @Test fun newlineFreeOutputIsDeliveredInBoundedChunks() {
        val shell = FakeShell(); val output = CopyOnWriteArrayList<String>()
        val session = TempShellSession(scope, { shell }, {}, { _, line -> output += line }, {})
        session.start(); waitFor { session.shellPid != null }
        val writer = Thread { shell.out.write("x".repeat(20000).toByteArray()); shell.out.flush(); shell.out.close() }
        writer.start(); writer.join(3000)
        waitFor { output.sumOf(String::length) == 20000 }
        assertTrue(output.all { it.length <= TempShellSession.MAX_LINE_CHARS })
        session.end(SessionEndReason.USER)
    }

    @Test fun rootCleanupSurvivesOwnerScopeCancellation() {
        val shell = FakeShell("4242:0:4242:123")
        val owners = CopyOnWriteArrayList<ShellIdentity>()
        val session = TempShellSession(scope, { shell }, {}, { _, _ -> }, {},
            requireRootIdentity = true, terminator = { owners += it })
        session.start(); waitFor { session.shellPid != null }
        scope.cancel()
        session.end(SessionEndReason.LEFT_SCREEN)
        waitFor { shell.destroyed }
        assertEquals(listOf(ShellIdentity(4242, 4242, 123)), owners)
    }

    @Test fun backgroundBeforeStartupStillExpiresSession() {
        val shell = FakeShell()
        val reasons = CopyOnWriteArrayList<SessionEndReason>()
        val session = TempShellSession(scope, { shell }, {}, { _, _ -> }, { reasons += it }, backgroundTimeoutMs = 30)
        session.onBackground()
        waitFor { reasons.isNotEmpty() }
        session.start()
        assertFalse(session.isRunning)
        assertTrue(shell.commands.isEmpty())
    }

}
