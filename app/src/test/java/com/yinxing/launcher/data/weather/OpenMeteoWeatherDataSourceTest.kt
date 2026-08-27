package com.yinxing.launcher.data.weather

import com.yinxing.launcher.data.weather.source.OpenMeteoWeatherDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherDataSourceTest {
    @Test
    fun `fetches current hourly and four day weather without an api key`() = runTest {
        val client = FakeWeatherApiClient { url ->
            if (url.contains("geocoding-api")) {
                """
                {
                  "results": [{
                    "name": "杭州",
                    "latitude": 30.29365,
                    "longitude": 120.16142,
                    "timezone": "Asia/Shanghai",
                    "country_code": "CN"
                  }]
                }
                """.trimIndent()
            } else {
                """
                {
                  "current": {
                    "time": "2026-08-27T09:30",
                    "temperature_2m": 30.5,
                    "relative_humidity_2m": 70,
                    "weather_code": 51,
                    "wind_speed_10m": 15.7,
                    "wind_direction_10m": 1
                  },
                  "hourly": {
                    "time": ["2026-08-27T09:00", "2026-08-27T12:00", "2026-08-27T15:00", "2026-08-27T20:00"],
                    "temperature_2m": [30.5, 32.0, 33.2, 28.4],
                    "weather_code": [51, 2, 3, 61],
                    "precipitation_probability": [20, 10, 30, 60]
                  },
                  "daily": {
                    "time": ["2026-08-27", "2026-08-28", "2026-08-29", "2026-08-30"],
                    "weather_code": [51, 95, 95, 2],
                    "temperature_2m_max": [33.1, 31.0, 33.8, 30.0],
                    "temperature_2m_min": [26.2, 25.8, 26.6, 24.0]
                  }
                }
                """.trimIndent()
            }
        }

        val weather = OpenMeteoWeatherDataSource(client).fetchWeather("杭州")

        requireNotNull(weather)
        assertEquals("30.29365,120.16142", weather.locationKey)
        assertEquals("小雨", weather.now.weather)
        assertEquals(31, weather.now.temperature)
        assertEquals("北风", weather.now.windDirection)
        assertEquals("3级", weather.now.windPower)
        assertEquals(70, weather.now.humidity)
        assertEquals("09:30", weather.now.updateTime)
        assertEquals(4, weather.forecast.size)
        assertEquals(33, weather.forecast.first().high)
        assertEquals("雷阵雨", weather.forecast[1].textDay)
        assertEquals(4, weather.hourly.size)
        assertEquals("2026-08-27T15:00", weather.hourly[2].time)
        assertEquals(33, weather.hourly[2].temperature)
        assertEquals(60, weather.hourly[3].precipitationProbability)
        assertTrue(client.urls.first().contains("name=%E6%9D%AD%E5%B7%9E"))
        assertTrue(client.urls.last().contains("forecast_days=4"))
        assertTrue(client.urls.last().contains("hourly=temperature_2m,weather_code,precipitation_probability"))
        assertTrue(client.urls.none { it.contains("key=") || it.contains("token=") })
    }

    @Test
    fun `returns null when the city cannot be geocoded`() = runTest {
        val client = FakeWeatherApiClient { "{\"results\":[]}" }

        val weather = OpenMeteoWeatherDataSource(client).fetchWeather("火星基地")

        assertEquals(null, weather)
        assertEquals(1, client.urls.size)
    }

    @Test
    fun `fetches weather directly from device coordinates without geocoding`() = runTest {
        val client = FakeWeatherApiClient { url ->
            require(!url.contains("geocoding-api"))
            """
            {
              "current": {
                "time": "2026-08-27T09:30",
                "temperature_2m": 27.0,
                "relative_humidity_2m": 68,
                "weather_code": 2,
                "wind_speed_10m": 5.0,
                "wind_direction_10m": 90
              },
              "hourly": {
                "time": ["2026-08-27T09:00"],
                "temperature_2m": [27.0],
                "weather_code": [2],
                "precipitation_probability": [10]
              },
              "daily": {
                "time": ["2026-08-27"],
                "weather_code": [2],
                "temperature_2m_max": [30.0],
                "temperature_2m_min": [22.0]
              }
            }
            """.trimIndent()
        }

        val weather = OpenMeteoWeatherDataSource(client)
            .fetchWeather(39.9042, 116.4074, "北京市")

        requireNotNull(weather)
        assertEquals("北京市", weather.now.cityName)
        assertEquals("39.9042,116.4074", weather.locationKey)
        assertEquals(1, client.urls.size)
        assertTrue(client.urls.single().contains("latitude=39.9042&longitude=116.4074"))
    }

    private class FakeWeatherApiClient(
        private val response: (String) -> String
    ) : WeatherApiClient {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            urls += url
            return response(url)
        }
    }
}
