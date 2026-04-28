package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallRiskHeuristicsTest {

    @Test
    fun repeatedUnknownGetsHighLocalSignal() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 3, 14)
        assertEquals("repeated_unknown", signal.label)
        assertTrue(signal.score >= 0.7f)
    }

    @Test
    fun shortCodeGetsShortCodeSignal() {
        val signal = IncomingCallRiskHeuristics.evaluate("12345", 0, 10)
        assertEquals("short_code", signal.label)
    }

    @Test
    fun overseasNumberGetsOverseasSignal() {
        val signal = IncomingCallRiskHeuristics.evaluate("+85212345678", 0, 11)
        assertEquals("overseas_or_masked", signal.label)
    }

    @Test
    fun regularUnknownNumberStaysLowSignal() {
        val signal = IncomingCallRiskHeuristics.evaluate("13800138000", 0, 15)
        assertEquals("unknown_number", signal.label)
        assertTrue(signal.score < 0.5f)
    }
}
