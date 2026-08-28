package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterCrashSnapshotTest {
    @Test
    fun `captures crash type and app frames without exception message`() {
        val throwable = IllegalStateException("联系人=张三 电话=13800138000").apply {
            stackTrace = arrayOf(
                StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 100),
                StackTraceElement("com.yinxing.launcher.feature.home.MainActivity", "render", "MainActivity.kt", 88)
            )
        }

        val event = LobsterCrashSnapshot.from(threadName = "worker-user-name", throwable).toUsageEvent()
        val serialized = "${event.summary}|${event.logLine}|${event.details.toJson()}"

        assertEquals("UNCAUGHT_EXCEPTION", event.details.errorCode)
        assertEquals("background_thread", event.details.failedStep)
        assertEquals(LobsterLogCategory.CRASH, event.category)
        assertEquals(LobsterEventType.ERROR, event.eventType)
        assertEquals("uncaught_exception", event.action)
        assertTrue(serialized.contains("IllegalStateException"))
        assertTrue(serialized.contains("MainActivity.render(MainActivity.kt:88)"))
        assertFalse(serialized.contains("张三"))
        assertFalse(serialized.contains("13800138000"))
        assertFalse(serialized.contains("worker-user-name"))
    }
}
