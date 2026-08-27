package com.yinxing.launcher.feature.weather

import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherNow
import com.yinxing.launcher.data.weather.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCityCardPresenterTest {
    @Test
    fun `city card presents current weather and today's range`() {
        val state = WeatherState.Success(
            cityName = "上海",
            adcode = "31.2,121.5",
            now = WeatherNow("上海", "多云", 31, "东风", "2级", 62, "10:44"),
            forecast = listOf(WeatherForecastDay("2026-08-27", "多云", "多云", 34, 26, "2")),
            lastFetchTime = 1L,
        )

        val card = WeatherCityCardPresenter.present(state, isCurrent = true)

        assertEquals("上海", card.city)
        assertEquals("31°", card.temperature)
        assertEquals("多云", card.condition)
        assertEquals("最高34° · 最低26°", card.highLow)
        assertEquals("更新于 10:44", card.updateTime)
        assertTrue(card.isCurrent)
    }
}
