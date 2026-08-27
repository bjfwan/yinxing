package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingPhoneStateEventCoalescerTest {

    @Test
    fun blankBroadcastFollowedByNumberKeepsNumberAndProcessesOnce() {
        val coalescer = IncomingPhoneStateEventCoalescer()

        val first = coalescer.record("RINGING", "")
        val second = coalescer.record("RINGING", "13812345678")

        assertEquals("13812345678", coalescer.claim(second)?.incomingNumber)
        assertNull(coalescer.claim(first))
    }

    @Test
    fun numberedBroadcastFollowedByBlankDoesNotLoseNumber() {
        val coalescer = IncomingPhoneStateEventCoalescer()

        val first = coalescer.record("RINGING", "13812345678")
        val second = coalescer.record("RINGING", "")

        assertEquals("13812345678", coalescer.claim(first)?.incomingNumber)
        assertNull(coalescer.claim(second))
    }

    @Test
    fun idleInvalidatesPendingRingingEvent() {
        val coalescer = IncomingPhoneStateEventCoalescer()

        val pending = coalescer.record("RINGING", "13812345678")
        coalescer.record("IDLE", "")

        assertNull(coalescer.claim(pending))
    }

    @Test
    fun nextCallCanBeClaimedAfterPreviousCallEnds() {
        val coalescer = IncomingPhoneStateEventCoalescer()

        val first = coalescer.record("RINGING", "13800000001")
        assertEquals("13800000001", coalescer.claim(first)?.incomingNumber)
        coalescer.record("IDLE", "")

        val second = coalescer.record("RINGING", "13800000002")
        assertEquals("13800000002", coalescer.claim(second)?.incomingNumber)
    }
}
