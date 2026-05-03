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

    @Test
    fun missingNumberGetsHighestScore() {
        val signal = IncomingCallRiskHeuristics.evaluate("", 0, 14)
        assertEquals("missing_number", signal.label)
        assertTrue(signal.score >= 0.8f)
    }

    @Test
    fun missingNumberWithWhitespaceOnly() {
        val signal = IncomingCallRiskHeuristics.evaluate("   ", 0, 14)
        assertEquals("missing_number", signal.label)
    }

    @Test
    fun serviceHotline95Prefix7Digits() {
        val signal = IncomingCallRiskHeuristics.evaluate("9558888", 0, 14)
        assertEquals("service_hotline", signal.label)
        assertTrue(signal.score >= 0.6f)
    }

    @Test
    fun serviceHotline95Longer() {
        val signal = IncomingCallRiskHeuristics.evaluate("95105888", 0, 14)
        assertEquals("service_hotline", signal.label)
    }

    @Test
    fun serviceHotline95ShortFallsToShortCode() {
        val signal = IncomingCallRiskHeuristics.evaluate("95588", 0, 14)
        assertEquals("short_code", signal.label)
    }

    @Test
    fun enterpriseHotline400Prefix() {
        val signal = IncomingCallRiskHeuristics.evaluate("4008001234", 0, 14)
        assertEquals("enterprise_hotline", signal.label)
        assertTrue(signal.score >= 0.56f)
    }

    @Test
    fun enterpriseHotline400Short() {
        val signal = IncomingCallRiskHeuristics.evaluate("40012", 0, 14)
        assertEquals("short_code", signal.label)
    }

    @Test
    fun overseasWith00Prefix() {
        val signal = IncomingCallRiskHeuristics.evaluate("001234567890", 0, 14)
        assertEquals("overseas_or_masked", signal.label)
    }

    @Test
    fun lateHourBefore8am() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 0, 5)
        assertEquals("late_hour_unknown", signal.label)
    }

    @Test
    fun lateHourAfter10pm() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 0, 23)
        assertEquals("late_hour_unknown", signal.label)
    }

    @Test
    fun exactly7amIsLateHour() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 0, 7)
        assertEquals("late_hour_unknown", signal.label)
    }

    @Test
    fun exactly8amIsNotLateHour() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 0, 8)
        assertEquals("unknown_number", signal.label)
    }

    @Test
    fun exactly10pmIsLateHour() {
        val signal = IncomingCallRiskHeuristics.evaluate("02112345678", 0, 22)
        assertEquals("late_hour_unknown", signal.label)
    }

    @Test
    fun shortCode3Digits() {
        val signal = IncomingCallRiskHeuristics.evaluate("100", 0, 14)
        assertEquals("short_code", signal.label)
    }

    @Test
    fun shortCode5Digits() {
        val signal = IncomingCallRiskHeuristics.evaluate("12345", 0, 14)
        assertEquals("short_code", signal.label)
    }

    @Test
    fun shortCode6DigitsIsNotShort() {
        val signal = IncomingCallRiskHeuristics.evaluate("123456", 0, 14)
        assertEquals("unknown_number", signal.label)
    }

    @Test
    fun repeatedOverridesShortCode() {
        val signal = IncomingCallRiskHeuristics.evaluate("12345", 3, 14)
        assertEquals("repeated_unknown", signal.label)
    }

    @Test
    fun missingNumberOverridesAll() {
        val signal = IncomingCallRiskHeuristics.evaluate("", 5, 23)
        assertEquals("missing_number", signal.label)
    }
}
