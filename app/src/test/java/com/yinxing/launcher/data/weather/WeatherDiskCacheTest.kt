package com.yinxing.launcher.data.weather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDiskCacheTest {
    private lateinit var cache: WeatherDiskCache

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cache = WeatherDiskCache(context)
        cache.clear()
    }

    @After
    fun tearDown() {
        cache.clear()
    }

    @Test
    fun `keeps cached weather for every saved city`() {
        cache.write(sampleState("北京", 28))
        cache.write(sampleState("杭州", 31))

        val beijing = cache.read("北京")
        val hangzhou = cache.read("杭州")

        assertNotNull(beijing)
        assertNotNull(hangzhou)
        assertEquals(28, beijing?.now?.temperature)
        assertEquals(31, hangzhou?.now?.temperature)
    }

    private fun sampleState(city: String, temperature: Int) = WeatherState.Success(
        cityName = city,
        adcode = city,
        now = WeatherNow(
            cityName = city,
            weather = "晴",
            temperature = temperature,
            windDirection = "北风",
            windPower = "2级",
            humidity = 50,
            updateTime = "13:00",
        ),
        forecast = listOf(
            WeatherForecastDay("2026-08-27", "晴", "晴", 32, 24, "0"),
        ),
        hourly = listOf(
            WeatherHour("2026-08-27T13:00", "晴", temperature, 0, "0"),
        ),
        lastFetchTime = 1_000L,
    )
}
