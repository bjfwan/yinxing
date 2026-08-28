package com.yinxing.launcher.feature.fall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UmaFallDatasetBenchmarkTest {

    @Test
    fun parserKeepsOnlyRightPocketAccelerometerSamples() {
        val trace = UmaFallTraceReader.parse(
            fileName = "UMAFall_Subject_02_Fall_forwardFall_1.csv",
            lines = sequenceOf(
                "%aa:bb; 0; RIGHTPOCKET; phone",
                "%cc:dd; 2; WAIST; sensor-tag",
                "100;1;0.1;0.2;0.3;0;0",
                "101;2;9.0;9.0;9.0;1;0",
                "102;3;8.0;8.0;8.0;0;2",
                "103;4;0.4;0.5;0.6;0;0"
            )
        )

        assertEquals(UmaFallLabel.Fall, trace.label)
        assertEquals(2, trace.samples.size)
        assertEquals(100L, trace.samples.first().timestampMs)
        assertEquals(0.6f, trace.samples.last().zG, 0.0001f)
    }

    @Test
    fun benchmarkCountsDetectionAndFalseAlarmByTrace() {
        val detectedFallSamples = mutableListOf<UmaFallSample>()
        for (timeMs in 0L..2_000L step 20L) {
            detectedFallSamples += UmaFallSample(timeMs, 0f, 1f, 0f)
        }
        for (timeMs in 2_020L..2_120L step 20L) {
            detectedFallSamples += UmaFallSample(timeMs, 3.5f, 0f, 0f)
        }
        for (timeMs in 2_140L..4_300L step 20L) {
            detectedFallSamples += UmaFallSample(timeMs, 1f, 0f, 0f)
        }
        val detectedFall = UmaFallTrace(
            name = "fall.csv",
            label = UmaFallLabel.Fall,
            samples = detectedFallSamples
        )
        val missedFall = syntheticTrace(
            name = "missed-fall.csv",
            label = UmaFallLabel.Fall,
            magnitudes = (0L..3_000L step 100L).map { it to 1.0f }
        )
        val normalActivity = syntheticTrace(
            name = "adl.csv",
            label = UmaFallLabel.Adl,
            magnitudes = (0L..3_000L step 100L).map { it to 1.0f }
        )

        val result = FallDatasetBenchmark.evaluate(
            sequenceOf(detectedFall, missedFall, normalActivity)
        )

        assertEquals(2, result.fallTraces)
        assertEquals(1, result.detectedFalls)
        assertEquals(1, result.adlTraces)
        assertEquals(0, result.falseAlarms)
        assertEquals(0.5, result.sensitivity, 0.0001)
        assertEquals(1.0, result.specificity, 0.0001)
        assertTrue(result.missedFallNames.contains("missed-fall.csv"))
    }

    @Test
    fun markdownReportContainsDatasetMetricsAndVerificationBoundary() {
        val result = FallDatasetBenchmarkResult(
            fallTraces = 2,
            detectedFalls = 1,
            adlTraces = 4,
            falseAlarms = 1,
            missedFallNames = listOf("missed-fall.csv"),
            falseAlarmNames = listOf("false-alarm.csv")
        )

        val report = result.toMarkdown(
            datasetName = "UMAFall corrected version",
            generatedAt = Instant.parse("2026-08-28T00:00:00Z")
        )

        assertTrue(report.contains("| 跌倒记录 | 2 |"))
        assertTrue(report.contains("| 检出率 | 50.00% |"))
        assertTrue(report.contains("| 误报记录 | 1 |"))
        assertTrue(report.contains("不能代表真机准确率"))
    }

    private fun syntheticTrace(
        name: String,
        label: UmaFallLabel,
        magnitudes: List<Pair<Long, Float>>
    ): UmaFallTrace = UmaFallTrace(
        name = name,
        label = label,
        samples = magnitudes.map { (timestampMs, magnitudeG) ->
            UmaFallSample(timestampMs, 0f, magnitudeG, 0f)
        }
    )
}
