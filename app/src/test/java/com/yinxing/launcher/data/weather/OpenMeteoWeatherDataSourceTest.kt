package com.yinxing.launcher.data.weather

import com.yinxing.launcher.data.weather.source.OpenMeteoWeatherDataSource
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertTrue(client.urls.isNotEmpty())
        assertTrue(client.urls.all { it.contains("geocoding-api") })
    }

    @Test
    fun `retries with suffix and split candidates until a confident match appears`() = runTest {
        val client = FakeWeatherApiClient { url ->
            when {
                url.contains("name=%E5%8C%97%E4%BA%AC%E6%9C%9D%E9%98%B3") -> """{"results":[]}"""
                url.contains("name=%E6%9C%9D%E9%98%B3") -> """
                    {"results":[{"name":"朝阳","latitude":31.4,"longitude":109.0,
                      "feature_code":"PPLA4","admin1":"重庆市","population":13524}]}
                    """.trimIndent()
                url.contains("name=%E5%8C%97%E4%BA%AC") -> """
                    {"results":[{"name":"北京","latitude":39.9,"longitude":116.4,
                      "feature_code":"PPLC","admin1":"北京市","population":21000000}]}
                    """.trimIndent()
                url.contains("forecast") -> """
                    {
                      "current": {"time":"2026-10-13T10:00","temperature_2m":20.0,
                        "relative_humidity_2m":50,"weather_code":0,
                        "wind_speed_10m":8.0,"wind_direction_10m":0},
                      "hourly": {"time":["2026-10-13T10:00"],"temperature_2m":[20.0],
                        "weather_code":[0],"precipitation_probability":[0]},
                      "daily": {"time":["2026-10-13"],"weather_code":[0],
                        "temperature_2m_max":[24.0],"temperature_2m_min":[15.0]}
                    }
                    """.trimIndent()
                else -> """{"results":[]}"""
            }
        }

        val weather = OpenMeteoWeatherDataSource(client).fetchWeather("北京朝阳")

        requireNotNull(weather)
        assertEquals("39.9,116.4", weather.locationKey)
        assertEquals("北京朝阳", weather.now.cityName)
        // 原始拼接查空 → 尾部“朝阳”命中乡镇但置信不足 → 继续拆到“北京”兜底
        assertTrue(client.urls.any { it.contains("name=%E5%8C%97%E4%BA%AC") })
    }

    @Test
    fun `candidateQueries completes suffixes for bare short names`() {
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        assertEquals(listOf("朝阳", "朝阳市", "朝阳县", "朝阳区"), source.candidateQueries("朝阳"))
    }

    @Test
    fun `candidateQueries splits concatenated input into tail and head`() {
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        val queries = source.candidateQueries("北京朝阳")

        assertEquals("北京朝阳", queries.first())
        assertTrue("朝阳" in queries && "北京" in queries)
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

    @Test
    fun `pickLocation prefers exact name match over first result`() {
        val results = JSONArray(
            """[
              {"name":"朝阳街道","latitude":41.5,"longitude":120.4,"admin2":"朝阳市","population":500},
              {"name":"朝阳区","latitude":39.9,"longitude":116.4,"admin1":"北京市","population":3600000}
            ]"""
        )
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        val picked = source.pickLocation(results, "朝阳区")

        assertEquals(39.9, picked?.getDouble("latitude") ?: 0.0, 0.001)
    }

    @Test
    fun `pickLocation uses admin context to disambiguate same-named districts`() {
        val results = JSONArray(
            """[
              {"name":"朝阳区","latitude":43.8,"longitude":125.3,"admin1":"吉林省","admin2":"长春市","population":700000},
              {"name":"朝阳区","latitude":39.9,"longitude":116.4,"admin1":"北京市","admin2":"北京市","population":3600000}
            ]"""
        )
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        val picked = source.pickLocation(results, "长春朝阳区")

        assertEquals(43.8, picked?.getDouble("latitude") ?: 0.0, 0.001)
    }

    @Test
    fun `pickLocation breaks ties by population`() {
        val results = JSONArray(
            """[
              {"name":"象山区","latitude":25.2,"longitude":110.3,"admin2":"桂林市","population":200000},
              {"name":"象山县","latitude":29.4,"longitude":121.8,"admin2":"宁波市","population":500000}
            ]"""
        )
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        val picked = source.pickLocation(results, "象山")

        assertEquals(29.4, picked?.getDouble("latitude") ?: 0.0, 0.001)
    }

    @Test
    fun `pickLocation returns null when nothing matches the query`() {
        val results = JSONArray(
            """[{"name":"哈尔滨市","latitude":45.8,"longitude":126.5,"population":9000000}]"""
        )
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        assertNull(source.pickLocation(results, "火星基地"))
    }

    @Test
    fun `normalizePlaceName strips administrative suffixes`() {
        val source = OpenMeteoWeatherDataSource(FakeWeatherApiClient { "{}" })

        assertEquals("北京", source.normalizePlaceName("北京市"))
        assertEquals("朝阳", source.normalizePlaceName("朝阳区"))
        assertEquals("象山", source.normalizePlaceName("象山县"))
        assertEquals("内蒙古", source.normalizePlaceName("内蒙古自治区"))
        assertEquals("凤凰古城", source.normalizePlaceName("凤凰古城"))
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
