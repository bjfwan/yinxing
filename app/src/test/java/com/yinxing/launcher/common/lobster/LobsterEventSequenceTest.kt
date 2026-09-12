package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Test

class LobsterEventSequenceTest {
    @Test
    fun `assigns an increasing sequence within one process session`() {
        val sequence = LobsterEventSequence()

        assertEquals(1L, sequence.next())
        assertEquals(2L, sequence.next())
        assertEquals(3L, sequence.next())
    }
}
