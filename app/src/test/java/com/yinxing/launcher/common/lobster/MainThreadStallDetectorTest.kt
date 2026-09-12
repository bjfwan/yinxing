package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainThreadStallDetectorTest {
    @Test
    fun `reports one event per continuous stall and resets after heartbeat`() {
        val detector = MainThreadStallDetector(thresholdMs = 8_000L)
        detector.onHeartbeat(1_000L)

        assertFalse(detector.shouldReport(nowMs = 8_999L, debuggerConnected = false))
        assertTrue(detector.shouldReport(nowMs = 9_001L, debuggerConnected = false))
        assertFalse(detector.shouldReport(nowMs = 20_000L, debuggerConnected = false))

        detector.onHeartbeat(20_001L)
        assertTrue(detector.shouldReport(nowMs = 28_002L, debuggerConnected = false))
    }

    @Test
    fun `does not report while debugger is connected`() {
        val detector = MainThreadStallDetector(thresholdMs = 8_000L)
        detector.onHeartbeat(1_000L)

        assertFalse(detector.shouldReport(nowMs = 20_000L, debuggerConnected = true))
    }

    @Test
    fun `returns measured duration for the reportable sample`() {
        val detector = MainThreadStallDetector(thresholdMs = 8_000L)
        detector.onHeartbeat(1_000L)

        assertEquals(8_001L, detector.takeReportableStallDuration(9_001L, debuggerConnected = false))
        assertNull(detector.takeReportableStallDuration(20_000L, debuggerConnected = false))
    }
}
