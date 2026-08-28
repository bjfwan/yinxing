package com.yinxing.launcher.feature.fall

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Short-lived guard around dialing, answering, and hanging up the phone. */
internal class FallCallTransitionGuard(
    private val windowMs: Long
) {
    private val guardedUntilElapsedMs = AtomicLong(Long.MIN_VALUE)
    private val callActive = AtomicBoolean(false)

    init {
        require(windowMs > 0L)
    }

    fun markTransition(nowElapsedMs: Long) {
        require(nowElapsedMs >= 0L)
        val nextUntil = if (nowElapsedMs > Long.MAX_VALUE - windowMs) {
            Long.MAX_VALUE
        } else {
            nowElapsedMs + windowMs
        }
        guardedUntilElapsedMs.updateAndGet { current -> maxOf(current, nextUntil) }
    }

    fun updateCallState(active: Boolean, nowElapsedMs: Long) {
        callActive.set(active)
        markTransition(nowElapsedMs)
    }

    fun isActive(nowElapsedMs: Long): Boolean {
        return callActive.get() ||
            (nowElapsedMs >= 0L && nowElapsedMs < guardedUntilElapsedMs.get())
    }

    fun reset() {
        callActive.set(false)
        guardedUntilElapsedMs.set(Long.MIN_VALUE)
    }
}

internal object FallCallTransitionContext {
    private const val WINDOW_MS = 10_000L
    private val guard = FallCallTransitionGuard(WINDOW_MS)

    fun markTransition(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        guard.markTransition(nowElapsedMs)
    }

    fun isActive(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return guard.isActive(nowElapsedMs)
    }

    fun updateCallState(
        active: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ) {
        guard.updateCallState(active, nowElapsedMs)
    }

    internal fun resetForTest() {
        guard.reset()
    }
}
