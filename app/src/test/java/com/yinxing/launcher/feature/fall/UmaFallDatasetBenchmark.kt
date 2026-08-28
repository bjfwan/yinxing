package com.yinxing.launcher.feature.fall

import java.time.Instant
import java.util.Locale

internal enum class UmaFallLabel {
    Fall,
    Adl
}

internal data class UmaFallSample(
    val timestampMs: Long,
    val xG: Float,
    val yG: Float,
    val zG: Float
)

internal data class UmaFallTrace(
    val name: String,
    val label: UmaFallLabel,
    val samples: List<UmaFallSample>
)

internal object UmaFallTraceReader {
    fun parse(fileName: String, lines: Sequence<String>): UmaFallTrace {
        var rightPocketSensorId: Int? = null
        val dataRows = mutableListOf<List<String>>()

        lines.forEach { line ->
            val columns = line.split(';').map(String::trim)
            if (line.startsWith('%') && columns.size >= 3 &&
                columns[2].equals("RIGHTPOCKET", ignoreCase = true)
            ) {
                rightPocketSensorId = columns[1].toIntOrNull()
            } else if (line.firstOrNull()?.isDigit() == true && columns.size >= 7) {
                dataRows += columns
            }
        }

        val phoneSensorId = requireNotNull(rightPocketSensorId) {
            "RIGHTPOCKET sensor is missing in $fileName"
        }
        val samples = dataRows.mapNotNull { columns ->
            val sensorType = columns[5].toIntOrNull()
            val sensorId = columns[6].toIntOrNull()
            if (sensorType != ACCELEROMETER_TYPE || sensorId != phoneSensorId) {
                null
            } else {
                UmaFallSample(
                    timestampMs = columns[0].toLong(),
                    xG = columns[2].toFloat(),
                    yG = columns[3].toFloat(),
                    zG = columns[4].toFloat()
                )
            }
        }

        return UmaFallTrace(
            name = fileName,
            label = if (fileName.contains("_Fall_", ignoreCase = true)) {
                UmaFallLabel.Fall
            } else {
                UmaFallLabel.Adl
            },
            samples = samples
        )
    }

    private const val ACCELEROMETER_TYPE = 0
}

internal data class FallDatasetBenchmarkResult(
    val fallTraces: Int,
    val detectedFalls: Int,
    val adlTraces: Int,
    val falseAlarms: Int,
    val missedFallNames: List<String>,
    val falseAlarmNames: List<String>
) {
    val sensitivity: Double
        get() = if (fallTraces == 0) 0.0 else detectedFalls.toDouble() / fallTraces

    val specificity: Double
        get() = if (adlTraces == 0) 0.0 else (adlTraces - falseAlarms).toDouble() / adlTraces

    fun toMarkdown(datasetName: String, generatedAt: Instant): String = buildString {
        appendLine("# 跌倒检测公开数据基准报告")
        appendLine()
        appendLine("- 数据集：$datasetName")
        appendLine("- 生成时间：$generatedAt")
        appendLine("- 检测逻辑：撞击峰值 → 瞬时方向变化 → 前后姿态变化")
        appendLine()
        appendLine("| 指标 | 结果 |")
        appendLine("| --- | ---: |")
        appendLine("| 跌倒记录 | $fallTraces |")
        appendLine("| 检出跌倒 | $detectedFalls |")
        appendLine("| 漏报记录 | ${fallTraces - detectedFalls} |")
        appendLine("| 检出率 | ${sensitivity.asPercent()} |")
        appendLine("| 日常活动记录 | $adlTraces |")
        appendLine("| 误报记录 | $falseAlarms |")
        appendLine("| 特异度 | ${specificity.asPercent()} |")
        appendLine()
        appendNameSection("漏报文件", missedFallNames)
        appendNameSection("误报文件", falseAlarmNames)
        appendLine("## 验证边界")
        appendLine()
        appendLine("这是公开实验数据的离线回放结果，不能代表真机准确率，也不能替代真实携带位置和长期误报测试。")
    }

    private fun StringBuilder.appendNameSection(title: String, names: List<String>) {
        appendLine("## $title")
        appendLine()
        if (names.isEmpty()) {
            appendLine("无。")
        } else {
            names.forEach { appendLine("- $it") }
        }
        appendLine()
    }

    private fun Double.asPercent(): String = String.format(Locale.US, "%.2f%%", this * 100.0)
}

internal object FallDatasetBenchmark {
    fun evaluate(traces: Sequence<UmaFallTrace>): FallDatasetBenchmarkResult {
        var fallTraces = 0
        var detectedFalls = 0
        var adlTraces = 0
        var falseAlarms = 0
        val missedFallNames = mutableListOf<String>()
        val falseAlarmNames = mutableListOf<String>()

        traces.forEach { trace ->
            val detected = detectsFall(trace)
            when (trace.label) {
                UmaFallLabel.Fall -> {
                    fallTraces += 1
                    if (detected) detectedFalls += 1 else missedFallNames += trace.name
                }
                UmaFallLabel.Adl -> {
                    adlTraces += 1
                    if (detected) {
                        falseAlarms += 1
                        falseAlarmNames += trace.name
                    }
                }
            }
        }

        return FallDatasetBenchmarkResult(
            fallTraces = fallTraces,
            detectedFalls = detectedFalls,
            adlTraces = adlTraces,
            falseAlarms = falseAlarms,
            missedFallNames = missedFallNames,
            falseAlarmNames = falseAlarmNames
        )
    }

    private fun detectsFall(trace: UmaFallTrace): Boolean {
        val engine = FallDetectionEngine()
        return trace.samples.any { sample ->
            engine.acceptG(sample.timestampMs, sample.xG, sample.yG, sample.zG) ==
                FallDetectionEvent.PossibleFall
        }
    }
}
