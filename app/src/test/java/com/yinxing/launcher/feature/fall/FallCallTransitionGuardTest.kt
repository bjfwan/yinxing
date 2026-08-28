package com.yinxing.launcher.feature.fall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallCallTransitionGuardTest {

    @Test
    fun transitionWindowExpiresAfterConfiguredDuration() {
        val guard = FallCallTransitionGuard(windowMs = 10_000L)

        guard.markTransition(1_000L)

        assertTrue(guard.isActive(10_999L))
        assertFalse(guard.isActive(11_000L))
    }

    @Test
    fun laterTransitionExtendsExistingWindow() {
        val guard = FallCallTransitionGuard(windowMs = 10_000L)

        guard.markTransition(1_000L)
        guard.markTransition(5_000L)

        assertTrue(guard.isActive(14_999L))
        assertFalse(guard.isActive(15_000L))
    }

    @Test
    fun activeCallKeepsGuardEnabledBeyondTransitionWindow() {
        val guard = FallCallTransitionGuard(windowMs = 10_000L)

        guard.updateCallState(active = true, nowElapsedMs = 1_000L)

        assertTrue(guard.isActive(60_000L))
    }

    @Test
    fun endingCallStartsPostCallProtectionWindow() {
        val guard = FallCallTransitionGuard(windowMs = 10_000L)
        guard.updateCallState(active = true, nowElapsedMs = 1_000L)

        guard.updateCallState(active = false, nowElapsedMs = 60_000L)

        assertTrue(guard.isActive(69_999L))
        assertFalse(guard.isActive(70_000L))
    }
}
