package com.yinxing.launcher.data.weather

import com.yinxing.launcher.data.weather.source.SeniverseWeatherDataSource
import com.yinxing.launcher.data.weather.source.TencentWeatherDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDataSourceTest {
    @Test
    fun tencentDataSourceParsesAdcodeAndRealtimeWeather() = runTest {
        val client = FakeWeatherApiClient { url ->
            if (url.contains("/district/")) {
                """
                {
                  "status": 0,
                  "result": [[
                    {"id": "110101", "level": 3},
                    {"id": "110000", "level": 1}
                  ]]
                }
                """.trimIndent()
            } else {
                """
                {
                  "status": 0,
                  "result": {
                    "realtime": [{
                      "update_time": "2026-04-27 08:30",
                      "infos": {
                        "weather": "晴",
                        "temperature": 23,
                        "wind_direction": "北风",
                        "wind_power": "1级",
                        "humidity": 45
                      }
                    }]
                  }
                }
                """.trimIndent()
            }
        }
        val source = TencentWeatherDataSource(client, apiKey = "key")

        val adcode = source.searchAdcode("北京")
        val now = source.fetchNow(adcode!!, "北京")

        assertEquals("110000", adcode)
        assertEquals("晴", now!!.weather)
        assertEquals(23, now.temperature)
        assertEquals("08:30", now.updateTime)
        assertTrue(client.urls.first().contains("keyword=%E5%8C%97%E4%BA%AC"))
    }

    @Test
    fun seniverseDataSourceParsesForecast() = runTest {
        val client = FakeWeatherApiClient {
            """
            {
              "results": [{
                "daily": [
                  {
                    "date": "2026-04-27",
                    "text_day": "多云",
                    "text_night": "晴",
                    "high": "26",
                    "low": "14",
                    "code_day": "4"
                  },
                  {
                    "date": "2026-04-28",
                    "text_day": "小雨",
                    "text_night": "阴",
                    "high": "22",
                    "low": "12",
                    "code_day": "13"
                  }
                ]
              }]
            }
            """.trimIndent()
        }
        val source = SeniverseWeatherDataSource(
            apiClient = client,
            uid = "uid",
            privateKey = "secret",
            clock = { 1_000_000L }
        )

        val forecast = source.fetchForecast("北京")

        assertEquals(2, forecast.size)
        assertEquals("多云", forecast[0].textDay)
        assertEquals(26, forecast[0].high)
        assertEquals("13", forecast[1].weatherCode)
        assertTrue(client.urls.single().contains("location=%E5%8C%97%E4%BA%AC"))
    }

    private class FakeWeatherApiClient(
        private val response: (String) -> String
    ) : WeatherApiClient {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            urls.add(url)
            return response(url)
        }
    }
}
