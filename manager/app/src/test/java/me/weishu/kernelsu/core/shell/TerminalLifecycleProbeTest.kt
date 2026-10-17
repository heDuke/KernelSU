package me.weishu.kernelsu.core.shell

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Audit probes for the unmodified feat/temp-terminal lifecycle implementation. */
class TerminalLifecycleProbeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Handle : ShellProcessHandle {
        override val stdin = ByteArrayOutputStream()
        override val stdout = ByteArrayInputStream(byteArrayOf())
        override val stderr = ByteArrayInputStream(byteArrayOf())
        @Volatile var destroyed = false
        override fun destroy() { destroyed = true }
    }

    @After fun closeScope() = scope.cancel()

    @Test fun endedSessionMustNotStartAnotherProcess() {
        val calls = AtomicInteger()
        val handle = Handle()
        val session = TempShellSession(scope, { calls.incrementAndGet(); handle }, {}, { _, _ -> }, {})
        try {
            session.end(SessionEndReason.LEFT_SCREEN)
            session.start()
            assertEquals("Leaving before startup must prevent process creation", 0, calls.get())
        } finally { handle.destroy() }
    }

    @Test fun leavingDuringStartupMustDestroyTheLateProcess() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val handle = Handle()
        val session = TempShellSession(scope, {
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            handle
        }, {}, { _, _ -> }, {})
        val thread = Thread { session.start() }
        try {
            thread.start()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            session.end(SessionEndReason.LEFT_SCREEN)
            release.countDown()
            thread.join(3000)
            assertFalse(thread.isAlive)
            assertTrue("The process created after session exit was never destroyed", handle.destroyed)
        } finally { release.countDown(); thread.join(3000); handle.destroy() }
    }
}
