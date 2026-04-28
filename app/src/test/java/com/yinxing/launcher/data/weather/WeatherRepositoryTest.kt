package com.yinxing.launcher.data.weather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.data.weather.source.SeniverseWeatherSource
import com.yinxing.launcher.data.weather.source.TencentWeatherSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import org.json.JSONException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherRepositoryTest {
    private lateinit var context: Context
    private lateinit var diskCache: WeatherDiskCache
    private var nowMillis = 1_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        diskCache = WeatherDiskCache(context)
        diskCache.clear()
        WeatherRepository.resetForTest()
        nowMillis = 1_000_000L
    }

    @After
    fun tearDown() {
        diskCache.clear()
        WeatherRepository.resetForTest()
    }

    @Test
    fun fetchWeatherPersistsSuccessAndCanRestoreFromDiskCache() = runTest {
        val tencentSource = FakeTencentWeatherSource()
        val seniverseSource = FakeSeniverseWeatherSource()
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val state = WeatherRepository.fetchWeather("北京")

        assertTrue(state is WeatherState.Success)
        state as WeatherState.Success
        assertEquals("北京", state.cityName)
        assertFalse(state.fromCache)
        assertEquals("晴", state.now.weather)
        assertEquals(1, tencentSource.searchCount)
        assertEquals(1, tencentSource.nowCount)
        assertEquals(1, seniverseSource.requestCount)

        WeatherRepository.clearMemoryCacheForTest()
        val restored = WeatherRepository.getCached()

        assertTrue(restored is WeatherState.Success)
        restored as WeatherState.Success
        assertTrue(restored.fromCache)
        assertEquals(state.lastFetchTime, restored.lastFetchTime)
        assertEquals(state.now.temperature, restored.now.temperature)
        assertEquals(state.forecast.first().textDay, restored.forecast.first().textDay)
    }

    @Test
    fun fetchWeatherReturnsCityNotFoundWhenAdcodeMissing() = runTest {
        val tencentSource = FakeTencentWeatherSource(adcode = null)
        val seniverseSource = FakeSeniverseWeatherSource()
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val state = WeatherRepository.fetchWeather("火星基地")

        assertTrue(state is WeatherState.CityNotFound)
        state as WeatherState.CityNotFound
        assertEquals("火星基地", state.cityName)
        assertEquals(1, tencentSource.searchCount)
        assertEquals(0, tencentSource.nowCount)
        assertEquals(0, seniverseSource.requestCount)
    }

    @Test
    fun fetchWeatherReturnsUsingCacheThenBackoffWhenRequestFailsAfterCacheExpires() = runTest {
        val tencentSource = FakeTencentWeatherSource()
        val seniverseSource = FakeSeniverseWeatherSource()
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val first = WeatherRepository.fetchWeather("北京")
        assertTrue(first is WeatherState.Success)

        nowMillis += 31 * 60 * 1000L
        tencentSource.nowError = IOException("timeout")

        val failedWithCache = WeatherRepository.fetchWeather("北京")

        assertTrue(failedWithCache is WeatherState.UsingCache)
        failedWithCache as WeatherState.UsingCache
        assertEquals(WeatherFailureReason.Network, failedWithCache.reason)
        assertTrue(failedWithCache.cached.fromCache)
        val searchCountAfterFailure = tencentSource.searchCount
        val nowCountAfterFailure = tencentSource.nowCount

        val backoffState = WeatherRepository.fetchWeather("北京")

        assertTrue(backoffState is WeatherState.UsingCache)
        backoffState as WeatherState.UsingCache
        assertEquals(WeatherFailureReason.Backoff, backoffState.reason)
        assertEquals(searchCountAfterFailure, tencentSource.searchCount)
        assertEquals(nowCountAfterFailure, tencentSource.nowCount)
    }

    @Test
    fun fetchWeatherReturnsFailureWithBackoffWithoutCache() = runTest {
        val tencentSource = FakeTencentWeatherSource(nowError = IOException("network down"))
        val seniverseSource = FakeSeniverseWeatherSource()
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val firstFailure = WeatherRepository.fetchWeather("北京")

        assertTrue(firstFailure is WeatherState.Failure)
        firstFailure as WeatherState.Failure
        assertEquals(WeatherFailureReason.Network, firstFailure.reason)
        val searchCountAfterFailure = tencentSource.searchCount
        val nowCountAfterFailure = tencentSource.nowCount

        val secondFailure = WeatherRepository.fetchWeather("北京")

        assertTrue(secondFailure is WeatherState.Failure)
        secondFailure as WeatherState.Failure
        assertEquals(WeatherFailureReason.Backoff, secondFailure.reason)
        assertEquals(searchCountAfterFailure, tencentSource.searchCount)
        assertEquals(nowCountAfterFailure, tencentSource.nowCount)
    }

    @Test
    fun fetchWeatherReturnsApiFailureWhenRealtimeWeatherIsNull() = runTest {
        val tencentSource = FakeTencentWeatherSource(now = null)
        val seniverseSource = FakeSeniverseWeatherSource()
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val state = WeatherRepository.fetchWeather("北京")

        assertTrue(state is WeatherState.Failure)
        state as WeatherState.Failure
        assertEquals(WeatherFailureReason.Api, state.reason)
    }

    @Test
    fun fetchWeatherReturnsParseFailureWhenForecastParsingThrows() = runTest {
        val tencentSource = FakeTencentWeatherSource()
        val seniverseSource = FakeSeniverseWeatherSource(error = JSONException("bad forecast"))
        WeatherRepository.configureForTest(
            tencentSource = tencentSource,
            seniverseSource = seniverseSource,
            diskCache = diskCache,
            clock = { nowMillis }
        )

        val state = WeatherRepository.fetchWeather("北京")

        assertTrue(state is WeatherState.Failure)
        state as WeatherState.Failure
        assertEquals(WeatherFailureReason.Parse, state.reason)
    }

    private class FakeTencentWeatherSource(
        var adcode: String? = "110000",
        var now: WeatherNow? = sampleNow(),
        var searchError: Exception? = null,
        var nowError: Exception? = null
    ) : TencentWeatherSource {
        var searchCount: Int = 0
        var nowCount: Int = 0

        override suspend fun searchAdcode(cityName: String): String? {
            searchCount += 1
            searchError?.let { throw it }
            return adcode
        }

        override suspend fun fetchNow(adcode: String, cityName: String): WeatherNow? {
            nowCount += 1
            nowError?.let { throw it }
            return now?.copy(cityName = cityName)
        }
    }

    private class FakeSeniverseWeatherSource(
        var forecast: List<WeatherForecastDay> = sampleForecast(),
        var error: Exception? = null
    ) : SeniverseWeatherSource {
        var requestCount: Int = 0

        override suspend fun fetchForecast(cityName: String): List<WeatherForecastDay> {
            requestCount += 1
            error?.let { throw it }
            return forecast
        }
    }

    companion object {
        private fun sampleNow(cityName: String = "北京") = WeatherNow(
            cityName = cityName,
            weather = "晴",
            temperature = 24,
            windDirection = "北风",
            windPower = "2级",
            humidity = 46,
            updateTime = "08:30"
        )

        private fun sampleForecast() = listOf(
            WeatherForecastDay(
                date = "2026-04-27",
                textDay = "多云",
                textNight = "晴",
                high = 26,
                low = 14,
                weatherCode = "4"
            ),
            WeatherForecastDay(
                date = "2026-04-28",
                textDay = "小雨",
                textNight = "阴",
                high = 22,
                low = 12,
                weatherCode = "13"
            )
        )
    }
}
