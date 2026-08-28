package com.yinxing.launcher.feature.fall

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

internal enum class FallDetectionEvent {
    None,
    PossibleFall
}

internal enum class FallDetectionContext {
    Normal,
    CallTransition
}

/**
 * Low-power accelerometer detector based on a four-second feature window.
 *
 * A candidate must combine a strong acceleration peak, rapid direction change around the
 * peak, and a lasting posture change between the one-second windows before and after it.
 * This avoids requiring an unrealistically long free-fall phase from a phone fixed to the body.
 */
internal class FallDetectionEngine(
    private val config: Config = Config()
) {
    internal data class Config(
        val impactThresholdL1G: Float = 3.2f,
        val minimumAngleVariationDegrees: Float = 4f,
        val minimumOrientationChangeDegrees: Float = 40f,
        val filterCutoffHz: Float = 5f,
        val nearImpactWindowMs: Long = 1_000L,
        val postureWindowMs: Long = 1_000L,
        val postureWindowOffsetMs: Long = 1_000L,
        val peakUpdateWindowMs: Long = 1_000L,
        val minimumWindowSamples: Int = 8,
        val historyCapacity: Int = 2_048,
        val fallbackSampleIntervalMs: Long = 5L
    )

    private enum class Stage {
        Monitoring,
        CollectingCandidate
    }

    private class VectorAverage {
        var count = 0
        var x = 0f
        var y = 0f
        var z = 0f

        fun add(sampleX: Float, sampleY: Float, sampleZ: Float) {
            count += 1
            x += sampleX
            y += sampleY
            z += sampleZ
        }
    }

    private val timestampsMs = LongArray(config.historyCapacity)
    private val filteredX = FloatArray(config.historyCapacity)
    private val filteredY = FloatArray(config.historyCapacity)
    private val filteredZ = FloatArray(config.historyCapacity)
    private val angleVariationsDegrees = FloatArray(config.historyCapacity)

    private var historyStart = 0
    private var historySize = 0
    private var stage = Stage.Monitoring
    private var lastTimestampMs = Long.MIN_VALUE
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var smoothedZ = 0f
    private var hasSmoothedSample = false
    private var candidateStartedAtMs = 0L
    private var candidatePeakAtMs = 0L
    private var candidatePeakL1G = 0f

    init {
        require(config.historyCapacity > 0)
        require(config.minimumWindowSamples > 0)
        require(config.filterCutoffHz > 0f)
    }

    fun accept(timestampNanos: Long, x: Float, y: Float, z: Float): FallDetectionEvent {
        return accept(timestampNanos, x, y, z, FallDetectionContext.Normal)
    }

    fun accept(
        timestampNanos: Long,
        x: Float,
        y: Float,
        z: Float,
        context: FallDetectionContext
    ): FallDetectionEvent {
        return acceptG(
            timestampMs = timestampNanos / NANOS_PER_MILLISECOND,
            xG = x / EARTH_GRAVITY,
            yG = y / EARTH_GRAVITY,
            zG = z / EARTH_GRAVITY,
            context = context
        )
    }

    internal fun acceptG(
        timestampMs: Long,
        xG: Float,
        yG: Float,
        zG: Float
    ): FallDetectionEvent {
        return acceptG(timestampMs, xG, yG, zG, FallDetectionContext.Normal)
    }

    internal fun acceptG(
        timestampMs: Long,
        xG: Float,
        yG: Float,
        zG: Float,
        context: FallDetectionContext
    ): FallDetectionEvent {
        if (!xG.isFinite() || !yG.isFinite() || !zG.isFinite()) return FallDetectionEvent.None
        if (lastTimestampMs != Long.MIN_VALUE && timestampMs < lastTimestampMs) reset()

        val hadPreviousSample = hasSmoothedSample
        val previousX = smoothedX
        val previousY = smoothedY
        val previousZ = smoothedZ
        smooth(timestampMs, xG, yG, zG)
        val angleVariation = if (hadPreviousSample) {
            angleDegrees(previousX, previousY, previousZ, smoothedX, smoothedY, smoothedZ)
        } else {
            0f
        }
        lastTimestampMs = timestampMs
        appendHistory(timestampMs, smoothedX, smoothedY, smoothedZ, angleVariation)

        if (context == FallDetectionContext.CallTransition) {
            stage = Stage.Monitoring
            clearCandidate()
            return FallDetectionEvent.None
        }

        val l1G = abs(smoothedX) + abs(smoothedY) + abs(smoothedZ)
        return when (stage) {
            Stage.Monitoring -> monitorForImpact(timestampMs, l1G)
            Stage.CollectingCandidate -> collectCandidate(timestampMs, l1G)
        }
    }

    fun reset() {
        historyStart = 0
        historySize = 0
        stage = Stage.Monitoring
        lastTimestampMs = Long.MIN_VALUE
        smoothedX = 0f
        smoothedY = 0f
        smoothedZ = 0f
        hasSmoothedSample = false
        clearCandidate()
    }

    private fun smooth(timestampMs: Long, xG: Float, yG: Float, zG: Float) {
        if (!hasSmoothedSample) {
            smoothedX = xG
            smoothedY = yG
            smoothedZ = zG
            hasSmoothedSample = true
            return
        }

        val elapsedMs = timestampMs - lastTimestampMs
        val sampleIntervalMs = if (elapsedMs > 0L) elapsedMs.coerceAtMost(MAX_FILTER_INTERVAL_MS)
        else config.fallbackSampleIntervalMs
        val intervalSeconds = sampleIntervalMs / 1_000f
        val rcSeconds = 1f / (2f * PI.toFloat() * config.filterCutoffHz)
        val alpha = intervalSeconds / (rcSeconds + intervalSeconds)
        smoothedX += alpha * (xG - smoothedX)
        smoothedY += alpha * (yG - smoothedY)
        smoothedZ += alpha * (zG - smoothedZ)
    }

    private fun monitorForImpact(timestampMs: Long, l1G: Float): FallDetectionEvent {
        if (l1G < config.impactThresholdL1G || !hasEnoughPreImpactHistory(timestampMs)) {
            return FallDetectionEvent.None
        }
        stage = Stage.CollectingCandidate
        candidateStartedAtMs = timestampMs
        candidatePeakAtMs = timestampMs
        candidatePeakL1G = l1G
        return FallDetectionEvent.None
    }

    private fun collectCandidate(timestampMs: Long, l1G: Float): FallDetectionEvent {
        if (timestampMs - candidateStartedAtMs <= config.peakUpdateWindowMs &&
            l1G > candidatePeakL1G
        ) {
            candidatePeakAtMs = timestampMs
            candidatePeakL1G = l1G
        }

        val requiredPostImpactMs = config.postureWindowOffsetMs + config.postureWindowMs
        if (timestampMs < candidatePeakAtMs + requiredPostImpactMs) {
            return FallDetectionEvent.None
        }

        val possibleFall = candidateHasFallFeatures()
        stage = Stage.Monitoring
        clearCandidate()
        return if (possibleFall) FallDetectionEvent.PossibleFall else FallDetectionEvent.None
    }

    private fun candidateHasFallFeatures(): Boolean {
        val nearImpactStart = candidatePeakAtMs - config.nearImpactWindowMs
        val nearImpactEnd = candidatePeakAtMs + config.nearImpactWindowMs
        var maximumAngleVariation = 0f
        forEachHistorySample { index ->
            val timestampMs = timestampsMs[index]
            if (timestampMs in nearImpactStart..nearImpactEnd) {
                maximumAngleVariation = maxOf(
                    maximumAngleVariation,
                    angleVariationsDegrees[index]
                )
            }
        }
        if (maximumAngleVariation < config.minimumAngleVariationDegrees) return false

        val beforeEnd = candidatePeakAtMs - config.postureWindowOffsetMs
        val beforeStart = beforeEnd - config.postureWindowMs
        val afterStart = candidatePeakAtMs + config.postureWindowOffsetMs
        val afterEnd = afterStart + config.postureWindowMs
        val before = averageVector(beforeStart, beforeEnd)
        val after = averageVector(afterStart, afterEnd)
        if (before.count < config.minimumWindowSamples || after.count < config.minimumWindowSamples) {
            return false
        }

        val orientationChange = angleDegrees(
            before.x / before.count,
            before.y / before.count,
            before.z / before.count,
            after.x / after.count,
            after.y / after.count,
            after.z / after.count
        )
        return orientationChange >= config.minimumOrientationChangeDegrees
    }

    private fun hasEnoughPreImpactHistory(timestampMs: Long): Boolean {
        val end = timestampMs - config.postureWindowOffsetMs
        val start = end - config.postureWindowMs
        var samples = 0
        forEachHistorySample { index ->
            if (timestampsMs[index] in start..end) samples += 1
        }
        return samples >= config.minimumWindowSamples
    }

    private fun averageVector(startMs: Long, endMs: Long): VectorAverage {
        val average = VectorAverage()
        forEachHistorySample { index ->
            if (timestampsMs[index] in startMs..endMs) {
                average.add(filteredX[index], filteredY[index], filteredZ[index])
            }
        }
        return average
    }

    private fun appendHistory(
        timestampMs: Long,
        x: Float,
        y: Float,
        z: Float,
        angleVariationDegrees: Float
    ) {
        val index = if (historySize < config.historyCapacity) {
            val next = (historyStart + historySize) % config.historyCapacity
            historySize += 1
            next
        } else {
            val next = historyStart
            historyStart = (historyStart + 1) % config.historyCapacity
            next
        }
        timestampsMs[index] = timestampMs
        filteredX[index] = x
        filteredY[index] = y
        filteredZ[index] = z
        angleVariationsDegrees[index] = angleVariationDegrees
    }

    private inline fun forEachHistorySample(block: (Int) -> Unit) {
        for (offset in 0 until historySize) {
            block((historyStart + offset) % config.historyCapacity)
        }
    }

    private fun clearCandidate() {
        candidateStartedAtMs = 0L
        candidatePeakAtMs = 0L
        candidatePeakL1G = 0f
    }

    private fun angleDegrees(
        firstX: Float,
        firstY: Float,
        firstZ: Float,
        secondX: Float,
        secondY: Float,
        secondZ: Float
    ): Float {
        val firstMagnitude = sqrt(firstX * firstX + firstY * firstY + firstZ * firstZ)
        val secondMagnitude = sqrt(secondX * secondX + secondY * secondY + secondZ * secondZ)
        val denominator = firstMagnitude * secondMagnitude
        if (denominator <= MINIMUM_VECTOR_MAGNITUDE) return 0f
        val cosine = (
            (firstX * secondX + firstY * secondY + firstZ * secondZ) / denominator
            ).coerceIn(-1f, 1f)
        return (acos(cosine) * 180f / PI.toFloat())
    }

    private companion object {
        const val EARTH_GRAVITY = 9.80665f
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_FILTER_INTERVAL_MS = 100L
        const val MINIMUM_VECTOR_MAGNITUDE = 0.0001f
    }
}
