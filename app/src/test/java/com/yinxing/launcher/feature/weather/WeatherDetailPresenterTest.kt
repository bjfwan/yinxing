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
    fun createsEightChronologicalHourlyRowsAndThreeRelativeForecastDays() {
        val ui = WeatherDetailPresenter.present(successState())

        assertEquals("北京", ui.city)
        assertEquals("今天 8月27日 周四", ui.date)
        assertEquals("27°", ui.temperature)
        assertEquals("晴", ui.condition)
        assertEquals("最高30° · 最低22°", ui.highLow)
        assertEquals(8, ui.hours.size)
        assertEquals(
            listOf("09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00"),
            ui.hours.map { it.time },
        )
        assertEquals("现在", ui.hours.first().label)
        assertEquals(listOf("27°", "28°", "28°", "29°", "29°", "30°", "30°", "29°"), ui.hours.map { it.temperature })
        assertEquals(listOf("降雨10%", "降雨10%", "降雨10%", "降雨10%", "降雨20%", "降雨20%", "降雨20%", "降雨30%"), ui.hours.map { it.precipitation })
        assertEquals(listOf("明天", "后天", "大后天"), ui.days.map { it.relativeDay })
        assertEquals(listOf("周五", "周六", "周日"), ui.days.map { it.weekday })
        assertEquals(listOf("多云", "阵雨", "多云"), ui.days.map { it.condition })
    }

    @Test
    fun hidesHourlySectionInsteadOfInventingValuesWhenCacheHasNoHourlyData() {
        val ui = WeatherDetailPresenter.present(successState().copy(hourly = emptyList()))

        assertTrue(ui.hours.isEmpty())
    }

    @Test
    fun keepsHourlyRowsChronologicalAcrossMidnight() {
        val state = successState().copy(
            now = WeatherNow("北京", "多云", 24, "东风", "1级", 70, "22:30"),
            hourly = listOf(
                hour("2026-08-27T22:00", "多云", 24, 10, "2"),
                hour("2026-08-27T23:00", "多云", 23, 10, "2"),
                hour("2026-08-28T00:00", "阴", 22, 20, "3"),
                hour("2026-08-28T01:00", "阴", 22, 20, "3"),
                hour("2026-08-28T02:00", "小雨", 21, 40, "61"),
                hour("2026-08-28T03:00", "小雨", 21, 50, "61"),
                hour("2026-08-28T04:00", "阴", 20, 20, "3"),
                hour("2026-08-28T05:00", "阴", 20, 10, "3"),
            ),
        )

        val ui = WeatherDetailPresenter.present(state)

        assertEquals(
            listOf("22:00", "23:00", "00:00", "01:00", "02:00", "03:00", "04:00", "05:00"),
            ui.hours.map { it.time },
        )
        assertEquals(listOf("现在", "晚上", "明天", "明天", "明天", "明天", "明天", "明天"), ui.hours.map { it.label })
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
            hour("2026-08-27T10:00", "晴", 28, 10, "0"),
            hour("2026-08-27T11:00", "晴", 28, 10, "0"),
            hour("2026-08-27T12:00", "晴", 29, 10, "0"),
            hour("2026-08-27T13:00", "多云", 29, 20, "2"),
            hour("2026-08-27T14:00", "多云", 30, 20, "2"),
            hour("2026-08-27T15:00", "多云", 30, 20, "2"),
            hour("2026-08-27T16:00", "阵雨", 29, 30, "80"),
            hour("2026-08-27T20:00", "多云", 25, 10, "2")
        ),
        lastFetchTime = 1L
    )

    private fun forecast(date: String, weather: String, high: Int, low: Int, code: String) =
        WeatherForecastDay(date, weather, weather, high, low, code)

    private fun hour(time: String, weather: String, temperature: Int, rain: Int, code: String) =
        WeatherHour(time, weather, temperature, rain, code)
}
