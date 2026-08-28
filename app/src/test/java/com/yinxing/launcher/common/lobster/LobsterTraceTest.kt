package com.yinxing.launcher.common.lobster

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LobsterTraceTest {
    @Test
    fun `creates unique UUID interaction ids`() {
        val first = LobsterTrace.newId()
        val second = LobsterTrace.newId()

        UUID.fromString(first)
        UUID.fromString(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `attaches a trace without losing structured error details`() {
        val traced = LobsterUsageEvents.OUTGOING_CALL_FAILED.withTrace("trace-1")

        assertEquals("trace-1", traced.details.traceId)
        assertEquals("PHONE_DIAL_FAILED", traced.details.errorCode)
        assertEquals("place_call", traced.action)
    }
}
