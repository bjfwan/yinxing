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

        val event = LobsterAnrEventFactory.from(frames)
        val stackLines = event.logLine.lines().filter { it.startsWith("at ") }

        assertEquals(30, stackLines.size)
        assertTrue(stackLines.first().contains("HomeActivity.render1"))
        assertFalse(event.logLine.contains("android.os.Looper"))
        assertEquals("MAIN_THREAD_STALLED", event.details.errorCode)
        assertEquals("detect_main_thread_stall", event.action)
    }

    @Test
    fun `uses a bounded fallback when no app frame exists`() {
        val frames = (1..30).map {
            StackTraceElement("android.os.Handler", "dispatch$it", "Handler.java", it)
        }

        val event = LobsterAnrEventFactory.from(frames)

        assertEquals(15, event.logLine.lines().count { it.startsWith("at ") })
    }
}
