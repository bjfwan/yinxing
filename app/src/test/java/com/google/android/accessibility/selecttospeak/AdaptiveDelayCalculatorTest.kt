package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.manager.TimeoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDelayCalculatorTest {

    @Test
    fun delayFor_clampsToProfileMin_whenStepTimeoutIsSmall() {
        val delay = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 100L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.FAST
        )
        assertEquals(DelayProfile.FAST.minDelay, delay)
    }

    @Test
    fun delayFor_clampsToProfileMax_whenStepTimeoutIsHuge() {
        val delay = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 1_000_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.STABLE
        )
        assertEquals(DelayProfile.STABLE.maxDelay, delay)
    }

    @Test
    fun delayFor_lowTierInflates_relativeToMidTier() {
        val low = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.LOW,
            profile = DelayProfile.TRANSITION
        )
        val mid = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.TRANSITION
        )
        assertTrue("low tier delay $low should be >= mid tier delay $mid", low >= mid)
    }

    @Test
    fun delayFor_highTierDeflates_relativeToMidTier() {
        val high = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.HIGH,
            profile = DelayProfile.TRANSITION
        )
        val mid = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.TRANSITION
        )
        assertTrue("high tier delay $high should be <= mid tier delay $mid", high <= mid)
    }

    @Test
    fun delayFor_attemptCountBoostsDelay_withinProfileMaxBound() {
        val noAttempts = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.STABLE,
            attemptCount = 0
        )
        val threeAttempts = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.STABLE,
            attemptCount = 3
        )
        assertTrue(threeAttempts >= noAttempts)
        assertTrue(threeAttempts <= DelayProfile.STABLE.maxDelay)
    }

    @Test
    fun delayFor_failureBoostsDelay() {
        val success = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.STABLE,
            actionSucceeded = true
        )
        val failure = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 8_000L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.STABLE,
            actionSucceeded = false
        )
        assertTrue(failure >= success)
    }

    @Test
    fun settleWindow_returnsAtLeastMinWindow() {
        val result = AdaptiveDelayCalculator.settleWindow(
            stepTimeoutMs = 100L,
            deviceTier = TimeoutManager.DeviceTier.MID,
            profile = DelayProfile.FAST,
            attemptCount = 0,
            minWindow = 5_000L
        )
        assertEquals(5_000L, result)
    }

    @Test
    fun settleWindow_returnsDelayFor_whenLargerThanMinWindow() {
        val delay = AdaptiveDelayCalculator.delayFor(
            stepTimeoutMs = 20_000L,
            deviceTier = TimeoutManager.DeviceTier.LOW,
            profile = DelayProfile.RECOVER
        )
        val result = AdaptiveDelayCalculator.settleWindow(
            stepTimeoutMs = 20_000L,
            deviceTier = TimeoutManager.DeviceTier.LOW,
            profile = DelayProfile.RECOVER,
            attemptCount = 0,
            minWindow = 0L
        )
        assertEquals(delay, result)
    }
}
