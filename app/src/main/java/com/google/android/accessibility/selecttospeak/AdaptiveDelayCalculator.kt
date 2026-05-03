package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.manager.TimeoutManager

internal enum class DelayProfile(
    val minDelay: Long,
    val maxDelay: Long,
    val timeoutDivisor: Long
) {
    FAST(80L, 220L, 90L),
    STABLE(120L, 320L, 55L),
    TRANSITION(160L, 480L, 36L),
    RECOVER(220L, 720L, 28L),
    WAIT_LOOP(260L, 760L, 26L),
    SHEET(200L, 650L, 24L)
}

internal object AdaptiveDelayCalculator {

    private const val ATTEMPT_BOOST_RATIO = 0.28f
    private const val FAILURE_BOOST_DIVISOR = 3L

    fun delayFor(
        stepTimeoutMs: Long,
        deviceTier: TimeoutManager.DeviceTier,
        profile: DelayProfile,
        attemptCount: Int = 0,
        actionSucceeded: Boolean? = null
    ): Long {
        val tierMultiplier = when (deviceTier) {
            TimeoutManager.DeviceTier.LOW -> 1.40f
            TimeoutManager.DeviceTier.MID -> 1.00f
            TimeoutManager.DeviceTier.HIGH -> 0.85f
        }
        val rawBase = (stepTimeoutMs / profile.timeoutDivisor * tierMultiplier).toLong()
        val base = rawBase.coerceIn(profile.minDelay, profile.maxDelay)
        val attemptBoost = if (attemptCount > 0) {
            (base * ATTEMPT_BOOST_RATIO * attemptCount).toLong()
        } else {
            0L
        }
        val failureBoost = if (actionSucceeded == false) base / FAILURE_BOOST_DIVISOR else 0L
        return (base + attemptBoost + failureBoost).coerceIn(profile.minDelay, profile.maxDelay)
    }

    fun settleWindow(
        stepTimeoutMs: Long,
        deviceTier: TimeoutManager.DeviceTier,
        profile: DelayProfile,
        attemptCount: Int,
        minWindow: Long
    ): Long {
        return delayFor(
            stepTimeoutMs = stepTimeoutMs,
            deviceTier = deviceTier,
            profile = profile,
            attemptCount = attemptCount
        ).coerceAtLeast(minWindow)
    }
}
