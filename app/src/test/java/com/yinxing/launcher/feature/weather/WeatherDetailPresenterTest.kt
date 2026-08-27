package com.yinxing.launcher.feature.weather

import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherHour
import com.yinxing.launcher.data.weather.WeatherNow
import com.yinxing.launcher.data.weather.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDetailPresenterTest {
    @Test
    fun createsElderFriendlyHourlyAdviceAndThreeRelativeForecastDays() {
        val ui = WeatherDetailPresenter.present(successState())

        assertEquals("北京", ui.city)
        assertEquals("今天 8月27日 周四", ui.date)
        assertEquals("27°", ui.temperature)
        assertEquals("晴", ui.condition)
        assertEquals("最高30° · 最低22°", ui.highLow)
        assertEquals("下午3点最热", ui.hottestAdvice)
        assertEquals("今天不用带伞", ui.rainAdvice)
        assertEquals(listOf("现在", "中午", "下午", "晚上"), ui.hours.map { it.label })
        assertEquals(listOf("27°", "29°", "30°", "25°"), ui.hours.map { it.temperature })
        assertEquals(listOf("明天", "后天", "大后天"), ui.days.map { it.relativeDay })
        assertEquals(listOf("周五", "周六", "周日"), ui.days.map { it.weekday })
        assertEquals(listOf("多云", "阵雨", "多云"), ui.days.map { it.condition })
    }

    @Test
    fun recommendsUmbrellaWhenHourlyRainProbabilityIsHigh() {
        val rainyHours = successState().hourly.map {
            if (it.time.endsWith("15:00")) it.copy(precipitationProbability = 70) else it
        }

        val ui = WeatherDetailPresenter.present(successState().copy(hourly = rainyHours))

        assertEquals("今天建议带伞", ui.rainAdvice)
    }

    @Test
    fun hidesHourlySectionInsteadOfInventingValuesWhenCacheHasNoHourlyData() {
        val ui = WeatherDetailPresenter.present(successState().copy(hourly = emptyList()))

        assertTrue(ui.hours.isEmpty())
    }

    private fun successState() = WeatherState.Success(
        cityName = "北京",
        adcode = "39.9042,116.4074",
        now = WeatherNow("北京", "晴", 27, "东风", "1级", 70, "09:30"),
        forecast = listOf(
            forecast("2026-08-27", "晴", 30, 22, "0"),
            forecast("2026-08-28", "多云", 29, 22, "2"),
            forecast("2026-08-29", "阵雨", 28, 21, "80"),
            forecast("2026-08-30", "多云", 27, 20, "2")
        ),
        hourly = listOf(
            hour("2026-08-27T09:00", "晴", 27, 10, "0"),
            hour("2026-08-27T12:00", "晴", 29, 10, "0"),
            hour("2026-08-27T15:00", "多云", 30, 20, "2"),
            hour("2026-08-27T20:00", "多云", 25, 10, "2")
        ),
        lastFetchTime = 1L
    )

    private fun forecast(date: String, weather: String, high: Int, low: Int, code: String) =
        WeatherForecastDay(date, weather, weather, high, low, code)

    private fun hour(time: String, weather: String, temperature: Int, rain: Int, code: String) =
        WeatherHour(time, weather, temperature, rain, code)
}
