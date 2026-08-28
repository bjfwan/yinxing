package com.yinxing.launcher.data.weather

import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.lobster.LobsterEventType
import com.yinxing.launcher.common.lobster.LobsterLogCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherUsageEventFactoryTest {
    @Test
    fun `maps weather failure to structured error without city or provider message`() {
        val state = WeatherState.Failure(
            cityName = "北京市朝阳区",
            reason = WeatherFailureReason.Network,
            error = "request failed for 北京市朝阳区"
        )

        val event = WeatherUsageEventFactory.from(state, 321L, "2026-08-27T10:00:00.000Z")
        val serialized = "${event.summary}|${event.logLine}|${event.details.toJson()}"

        assertEquals(LobsterReportStatus.ERROR, event.status)
        assertEquals("WEATHER_NETWORK_ERROR", event.details.errorCode)
        assertEquals(321L, event.details.steps.single().durationMs)
        assertEquals(LobsterLogCategory.WEATHER, event.category)
        assertEquals(LobsterEventType.ERROR, event.eventType)
        assertEquals("refresh_weather", event.action)
        assertFalse(serialized.contains("北京市朝阳区"))
        assertFalse(serialized.contains("request failed"))
    }

    @Test
    fun `distinguishes fresh cache and degraded cache`() {
        val success = WeatherState.Success(
            cityName = "北京",
            adcode = "110000",
            now = WeatherNow("北京", "晴", 30, "北", "2", 40, "10:00"),
            forecast = emptyList(),
            lastFetchTime = 1L,
            fromCache = true
        )

        val cached = WeatherUsageEventFactory.from(success, 8L, "2026-08-27T10:00:00.000Z")
        val degraded = WeatherUsageEventFactory.from(
            WeatherState.UsingCache(success, WeatherFailureReason.Api, "provider failed"),
            88L,
            "2026-08-27T10:00:00.000Z"
        )

        assertEquals(LobsterReportStatus.SUCCESS, cached.status)
        assertEquals("天气缓存命中", cached.summary)
        assertEquals(LobsterReportStatus.ERROR, degraded.status)
        assertEquals("WEATHER_API_ERROR", degraded.details.errorCode)
    }
}
