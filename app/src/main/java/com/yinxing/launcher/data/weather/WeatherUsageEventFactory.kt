package com.yinxing.launcher.data.weather

import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.lobster.LobsterUsageEvent
import com.yinxing.launcher.common.lobster.LobsterEventType
import com.yinxing.launcher.common.lobster.LobsterLogCategory
import java.security.MessageDigest

object WeatherUsageEventFactory {
    fun from(state: WeatherState, durationMs: Long, occurredAt: String): LobsterUsageEvent {
        val status = when (state) {
            is WeatherState.Success -> LobsterReportStatus.SUCCESS
            is WeatherState.Loading -> LobsterReportStatus.REPORTED
            else -> LobsterReportStatus.ERROR
        }
        val summary = when (state) {
            is WeatherState.Success -> if (state.fromCache) "天气缓存命中" else "天气更新成功"
            is WeatherState.UsingCache -> "天气更新失败，已使用缓存"
            is WeatherState.CityNotFound -> "天气城市查询失败"
            is WeatherState.Failure -> "天气更新失败"
            is WeatherState.Loading -> "天气查询处理中"
        }
        val errorCode = when (state) {
            is WeatherState.UsingCache -> state.reason.toErrorCode()
            is WeatherState.Failure -> state.reason.toErrorCode()
            is WeatherState.CityNotFound -> "WEATHER_CITY_NOT_FOUND"
            else -> null
        }
        val outcome = if (status == LobsterReportStatus.ERROR) {
            LobsterStepOutcome.ERROR
        } else {
            LobsterStepOutcome.SUCCESS
        }
        return LobsterUsageEvent(
            scene = "天气查询",
            status = status,
            summary = summary,
            logLine = "[天气] $summary",
            category = LobsterLogCategory.WEATHER,
            eventType = if (status == LobsterReportStatus.ERROR) LobsterEventType.ERROR else LobsterEventType.DIAGNOSTIC,
            action = "refresh_weather",
            details = LobsterReportDetails(
                errorCode = errorCode,
                failedStep = if (status == LobsterReportStatus.ERROR) "fetch_weather" else null,
                steps = listOf(
                    LobsterTraceStep(
                        stepCode = "fetch_weather",
                        stepName = "获取天气",
                        action = "request",
                        outcome = outcome,
                        detail = if (status == LobsterReportStatus.ERROR) {
                            "city_hash=${cityFingerprint(state.cityName)}"
                        } else {
                            null
                        },
                        durationMs = durationMs.coerceAtLeast(0L),
                        occurredAt = occurredAt
                    )
                )
            )
        )
    }

    /**
     * 定位来源命中统计：只上报 source 与耗时，不含坐标和城市名。
     * 用于观察 beaconDB 指纹库（radio）在真实设备上的命中率，
     * 决定后续是否需要自建/补充指纹数据。
     */
    fun locationResolved(
        source: String,
        durationMs: Long,
        approximate: Boolean,
        occurredAt: String,
    ): LobsterUsageEvent {
        val failed = source == "none"
        val outcome = if (failed) LobsterStepOutcome.ERROR else LobsterStepOutcome.SUCCESS
        return LobsterUsageEvent(
            scene = "天气定位",
            status = if (failed) LobsterReportStatus.ERROR else LobsterReportStatus.SUCCESS,
            summary = if (failed) "定位失败" else "定位成功($source)",
            logLine = "[天气] 定位 source=$source duration=${durationMs}ms approximate=$approximate",
            category = LobsterLogCategory.WEATHER,
            eventType = if (failed) LobsterEventType.ERROR else LobsterEventType.DIAGNOSTIC,
            action = "resolve_location",
            details = LobsterReportDetails(
                errorCode = if (failed) "LOCATION_FAILED" else null,
                failedStep = if (failed) "resolve_location" else null,
                steps = listOf(
                    LobsterTraceStep(
                        stepCode = "resolve_location",
                        stepName = "定位",
                        action = "request",
                        outcome = outcome,
                        detail = "source=$source approximate=$approximate",
                        durationMs = durationMs.coerceAtLeast(0L),
                        occurredAt = occurredAt
                    )
                )
            )
        )
    }

    private fun cityFingerprint(cityName: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cityName.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun WeatherFailureReason.toErrorCode(): String = when (this) {
        WeatherFailureReason.Network -> "WEATHER_NETWORK_ERROR"
        WeatherFailureReason.Api -> "WEATHER_API_ERROR"
        WeatherFailureReason.Parse -> "WEATHER_PARSE_ERROR"
        WeatherFailureReason.Backoff -> "WEATHER_BACKOFF"
        WeatherFailureReason.Unknown -> "WEATHER_UNKNOWN_ERROR"
    }
}
