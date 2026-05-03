package com.yinxing.launcher.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherStateTest {

    @Test
    fun loadingIsNotValid() {
        val state = WeatherState.Loading("北京")
        assertFalse(state.isValid)
        assertNull(state.now)
        assertNull(state.error)
    }

    @Test
    fun successIsValid() {
        val state = sampleSuccess()
        assertTrue(state.isValid)
        assertNotNull(state.now)
        assertNull(state.error)
        assertFalse(state.fromCache)
    }

    @Test
    fun successFromCacheIsValid() {
        val state = sampleSuccess(fromCache = true)
        assertTrue(state.isValid)
        assertTrue(state.fromCache)
    }

    @Test
    fun failureIsNotValid() {
        val state = WeatherState.Failure("北京", WeatherFailureReason.Network, "timeout")
        assertFalse(state.isValid)
        assertNull(state.now)
        assertNotNull(state.error)
        assertEquals(WeatherFailureReason.Network, state.reason)
    }

    @Test
    fun failureReasons() {
        assertEquals(5, WeatherFailureReason.entries.size)
        assertNotNull(WeatherFailureReason.Network)
        assertNotNull(WeatherFailureReason.Api)
        assertNotNull(WeatherFailureReason.Parse)
        assertNotNull(WeatherFailureReason.Backoff)
        assertNotNull(WeatherFailureReason.Unknown)
    }

    @Test
    fun cityNotFoundIsNotValid() {
        val state = WeatherState.CityNotFound("火星")
        assertFalse(state.isValid)
        assertNull(state.now)
        assertNotNull(state.error)
    }

    @Test
    fun usingCacheIsValid() {
        val cached = sampleSuccess()
        val state = WeatherState.UsingCache(cached, WeatherFailureReason.Network, "使用缓存")
        assertTrue(state.isValid)
        assertNotNull(state.now)
        assertNotNull(state.error)
        assertEquals(cached.cityName, state.cityName)
        assertEquals(cached.lastFetchTime, state.lastFetchTime)
    }

    @Test
    fun emptyReturnsLoadingWithBlankCity() {
        val state = WeatherState.empty()
        assertTrue(state is WeatherState.Loading)
        assertEquals("", state.cityName)
    }

    private fun sampleSuccess(fromCache: Boolean = false): WeatherState.Success {
        return WeatherState.Success(
            cityName = "北京",
            adcode = "110000",
            now = WeatherNow(
                cityName = "北京",
                weather = "晴",
                temperature = 24,
                windDirection = "北风",
                windPower = "2级",
                humidity = 46,
                updateTime = "08:30"
            ),
            forecast = listOf(
                WeatherForecastDay(
                    date = "2026-04-27",
                    textDay = "多云",
                    textNight = "晴",
                    high = 26,
                    low = 14,
                    weatherCode = "4"
                )
            ),
            lastFetchTime = 1000L,
            fromCache = fromCache
        )
    }
}
