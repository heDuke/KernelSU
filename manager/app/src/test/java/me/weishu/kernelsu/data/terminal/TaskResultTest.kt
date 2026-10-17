package me.weishu.kernelsu.data.terminal
import org.junit.Assert.assertEquals
import org.junit.Test
class TaskResultTest {
    @Test fun cancellationRequiresObservedSignalTermination() {
        assertEquals(TaskStatus.COMPLETED, classifyTaskExit(TaskExit(0, 0), true))
        assertEquals(TaskStatus.FAILED, classifyTaskExit(TaskExit(7, 0), true))
        assertEquals(TaskStatus.CANCELED, classifyTaskExit(TaskExit(-1, 15), true))
        assertEquals(TaskStatus.FAILED, classifyTaskExit(TaskExit(-1, 2), false))
        assertEquals(TaskStatus.INTERRUPTED, classifyTaskExit(TaskExit(-1, 0), true))
    }
}
