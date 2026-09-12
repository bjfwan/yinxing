package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobsterAnrEventFactoryTest {
    @Test
    fun `keeps a bounded app stack without runtime values`() {
        val frames = (1..40).map {
            StackTraceElement("com.yinxing.launcher.feature.home.HomeActivity", "render$it", "HomeActivity.kt", it)
        } + StackTraceElement("android.os.Looper", "loop", "Looper.java", 100)

        val event = LobsterAnrEventFactory.from(frames, stallDurationMs = 9_321L)
        val stackLines = event.logLine.lines().filter { it.startsWith("at ") }

        assertEquals(30, stackLines.size)
        assertTrue(stackLines.first().contains("HomeActivity.render1"))
        assertFalse(event.logLine.contains("android.os.Looper"))
        assertEquals("MAIN_THREAD_STALLED", event.details.errorCode)
        assertEquals("detect_main_thread_stall", event.action)
        assertTrue(event.logLine.contains("duration_ms=9321"))
    }

    @Test
    fun `uses a bounded fallback when no app frame exists`() {
        val frames = (1..30).map {
            StackTraceElement("android.os.Handler", "dispatch$it", "Handler.java", it)
        }

        val event = LobsterAnrEventFactory.from(frames)

        assertEquals(15, event.logLine.lines().count { it.startsWith("at ") })
    }

    @Test
    fun `classifies an idle main loop sample as recovered instead of an error`() {
        val frames = listOf(
            StackTraceElement("android.os.MessageQueue", "nativePollOnce", "MessageQueue.java", -2),
            StackTraceElement("android.os.MessageQueue", "next", "MessageQueue.java", 335),
            StackTraceElement("android.os.Looper", "loopOnce", "Looper.java", 161),
            StackTraceElement("android.os.Looper", "loop", "Looper.java", 288),
        )

        assertEquals(MainThreadStallSample.RECOVERED_IDLE, MainThreadStallSampleClassifier.classify(frames))
    }

    @Test
    fun `keeps an app or accessibility wait stack actionable`() {
        val frames = listOf(
            StackTraceElement(
                "android.view.accessibility.AccessibilityInteractionClient",
                "waitForResultTimedLocked",
                "AccessibilityInteractionClient.java",
                905,
            ),
            StackTraceElement(
                "com.yinxing.launcher.automation.wechat.WeChatRootProvider",
                "getRoot",
                "WeChatRootProvider.kt",
                42,
            ),
        )

        assertEquals(MainThreadStallSample.ACTIONABLE, MainThreadStallSampleClassifier.classify(frames))
    }

    @Test
    fun `classifies watchdog self sampling as non actionable`() {
        val frames = listOf(
            StackTraceElement(
                "com.yinxing.launcher.common.lobster.LobsterMainThreadWatchdog",
                "start\$lambda\$2",
                "LobsterMainThreadWatchdog.kt",
                80,
            ),
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 1012),
        )

        assertEquals(MainThreadStallSample.WATCHDOG_SELF, MainThreadStallSampleClassifier.classify(frames))
    }
}
