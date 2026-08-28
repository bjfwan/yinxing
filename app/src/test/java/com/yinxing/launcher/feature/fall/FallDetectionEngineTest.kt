package com.yinxing.launcher.feature.fall

import org.junit.Assert.assertEquals
import org.junit.Test

class FallDetectionEngineTest {

    @Test
    fun impactAndLargeOrientationChangeTriggersWithoutExtendedFreeFall() {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f))
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 3.5f, 0f, 0f))
        }
        for (timeMs in 2_140L..4_300L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 1f, 0f, 0f))
        }

        assertEquals(FallDetectionEvent.PossibleFall, event)
    }

    @Test
    fun hardImpactWithoutOrientationChangeDoesNotTrigger() {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f))
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 3.5f, 0f))
        }
        for (timeMs in 2_140L..4_300L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f))
        }

        assertEquals(FallDetectionEvent.None, event)
    }

    @Test
    fun orientationChangeWithoutImpactDoesNotTrigger() {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f))
        }
        for (timeMs in 2_020L..4_300L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 1f, 0f, 0f))
        }

        assertEquals(FallDetectionEvent.None, event)
    }

    @Test
    fun impactBeforeEnoughHistoryDoesNotTrigger() {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..100L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 3.5f, 0f, 0f))
        }
        for (timeMs in 120L..2_500L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 1f, 0f, 0f))
        }

        assertEquals(FallDetectionEvent.None, event)
    }

    @Test
    fun resetClearsPendingCandidate() {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f))
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 3.5f, 0f, 0f))
        }
        engine.reset()
        for (timeMs in 2_140L..4_300L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 1f, 0f, 0f))
        }

        assertEquals(FallDetectionEvent.None, event)
    }

    @Test
    fun phoneToEarMotionIsRejectedDuringCallTransition() {
        val event = runOrientationChangeSequence(
            impactG = 3.5f,
            context = FallDetectionContext.CallTransition
        )

        assertEquals(FallDetectionEvent.None, event)
    }

    @Test
    fun evenSevereImpactIsRejectedDuringCallTransition() {
        val event = runOrientationChangeSequence(
            impactG = 5.5f,
            context = FallDetectionContext.CallTransition
        )

        assertEquals(FallDetectionEvent.None, event)
    }

    private fun runOrientationChangeSequence(
        impactG: Float,
        context: FallDetectionContext
    ): FallDetectionEvent {
        val engine = FallDetectionEngine()
        var event = FallDetectionEvent.None

        for (timeMs in 0L..2_000L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 0f, 1f, 0f, context))
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, impactG, 0f, 0f, context))
        }
        for (timeMs in 2_140L..4_300L step 20L) {
            event = maxEvent(event, engine.acceptGVector(timeMs, 1f, 0f, 0f, context))
        }
        return event
    }

    private fun FallDetectionEngine.acceptGVector(
        timestampMs: Long,
        xG: Float,
        yG: Float,
        zG: Float,
        context: FallDetectionContext = FallDetectionContext.Normal
    ): FallDetectionEvent = accept(
        timestampNanos = timestampMs * 1_000_000L,
        x = xG * 9.80665f,
        y = yG * 9.80665f,
        z = zG * 9.80665f,
        context = context
    )

    private fun maxEvent(
        first: FallDetectionEvent,
        second: FallDetectionEvent
    ): FallDetectionEvent = if (
        first == FallDetectionEvent.PossibleFall || second == FallDetectionEvent.PossibleFall
    ) {
        FallDetectionEvent.PossibleFall
    } else {
        FallDetectionEvent.None
    }
}
