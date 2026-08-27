package com.yinxing.launcher.data.weather

import com.yinxing.launcher.common.lobster.LobsterReportDetails
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterStepOutcome
import com.yinxing.launcher.common.lobster.LobsterTraceStep
import com.yinxing.launcher.common.lobster.LobsterUsageEvent

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
            details = LobsterReportDetails(
                errorCode = errorCode,
                failedStep = if (status == LobsterReportStatus.ERROR) "fetch_weather" else null,
                steps = listOf(
                    LobsterTraceStep(
                        stepCode = "fetch_weather",
                        stepName = "获取天气",
                        action = "request",
                        outcome = outcome,
                        durationMs = durationMs.coerceAtLeast(0L),
                        occurredAt = occurredAt
                    )
                )
            )
        )
    }

    private fun WeatherFailureReason.toErrorCode(): String = when (this) {
        WeatherFailureReason.Network -> "WEATHER_NETWORK_ERROR"
        WeatherFailureReason.Api -> "WEATHER_API_ERROR"
        WeatherFailureReason.Parse -> "WEATHER_PARSE_ERROR"
        WeatherFailureReason.Backoff -> "WEATHER_BACKOFF"
        WeatherFailureReason.Unknown -> "WEATHER_UNKNOWN_ERROR"
    }
}
