package com.yinxing.launcher.data.weather.source

import com.yinxing.launcher.data.weather.WeatherApiClient
import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherHour
import com.yinxing.launcher.data.weather.WeatherNow
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherSourceResult(
    val locationKey: String,
    val now: WeatherNow,
    val forecast: List<WeatherForecastDay>,
    val hourly: List<WeatherHour> = emptyList()
)

interface WeatherSource {
    suspend fun fetchWeather(cityName: String): WeatherSourceResult?
    suspend fun fetchWeather(latitude: Double, longitude: Double, cityName: String): WeatherSourceResult? = null
}

class OpenMeteoWeatherDataSource(
    private val apiClient: WeatherApiClient
) : WeatherSource {
    override suspend fun fetchWeather(cityName: String): WeatherSourceResult? {
        val location = fetchLocation(cityName) ?: return null
        return fetchWeather(location.latitude, location.longitude, cityName)
    }

    override suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        cityName: String,
    ): WeatherSourceResult {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "定位坐标无效" }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=4"
        val root = JSONObject(apiClient.get(url))
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourlyJson = root.getJSONObject("hourly")
        val currentCode = current.getInt("weather_code")
        val now = WeatherNow(
            cityName = cityName,
            weather = weatherText(currentCode),
            temperature = current.getDouble("temperature_2m").roundToInt(),
            windDirection = windDirection(current.getDouble("wind_direction_10m")),
            windPower = windPower(current.getDouble("wind_speed_10m")),
            humidity = current.getDouble("relative_humidity_2m").roundToInt().coerceIn(0, 100),
            updateTime = current.getString("time").substringAfter('T').take(5)
        )
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val count = minOf(dates.length(), codes.length(), highs.length(), lows.length(), 4)
        val forecast = (0 until count).map { index ->
            val code = codes.getInt(index)
            WeatherForecastDay(
                date = dates.getString(index),
                textDay = weatherText(code),
                textNight = weatherText(code),
                high = highs.getDouble(index).roundToInt(),
                low = lows.getDouble(index).roundToInt(),
                weatherCode = code.toString()
            )
        }
        check(forecast.isNotEmpty()) { "天气预报为空" }
        val hourTimes = hourlyJson.getJSONArray("time")
        val hourTemperatures = hourlyJson.getJSONArray("temperature_2m")
        val hourCodes = hourlyJson.getJSONArray("weather_code")
        val rainProbabilities = hourlyJson.getJSONArray("precipitation_probability")
        val hourCount = minOf(
            hourTimes.length(),
            hourTemperatures.length(),
            hourCodes.length(),
            rainProbabilities.length()
        )
        val hourly = (0 until hourCount).map { index ->
            val code = hourCodes.getInt(index)
            WeatherHour(
                time = hourTimes.getString(index),
                weather = weatherText(code),
                temperature = hourTemperatures.getDouble(index).roundToInt(),
                precipitationProbability = rainProbabilities.optInt(index, 0).coerceIn(0, 100),
                weatherCode = code.toString()
            )
        }
        return WeatherSourceResult(
            locationKey = "$latitude,$longitude",
            now = now,
            forecast = forecast,
            hourly = hourly
        )
    }

    private suspend fun fetchLocation(cityName: String): Location? {
        val encoded = URLEncoder.encode(cityName, Charsets.UTF_8.name())
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=1&language=zh&format=json&countryCode=CN"
        val results = JSONObject(apiClient.get(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val item = results.getJSONObject(0)
        val latitude = item.getDouble("latitude")
        val longitude = item.getDouble("longitude")
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "城市坐标无效" }
        return Location(latitude, longitude)
    }

    private data class Location(val latitude: Double, val longitude: Double)

    private fun weatherText(code: Int): String = when (code) {
        0, 1 -> "晴"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55, 56, 57, 61 -> "小雨"
        63, 66 -> "中雨"
        65, 67 -> "大雨"
        80, 81, 82 -> "阵雨"
        71, 77, 85 -> "小雪"
        73 -> "中雪"
        75, 86 -> "大雪"
        95, 96, 99 -> "雷阵雨"
        else -> "天气变化"
    }

    private fun windDirection(degrees: Double): String {
        val normalized = ((degrees % 360) + 360) % 360
        val directions = arrayOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")
        return directions[((normalized + 22.5) / 45.0).toInt() % directions.size]
    }

    private fun windPower(speedKmh: Double): String {
        val level = when {
            speedKmh < 1 -> 0
            speedKmh < 6 -> 1
            speedKmh < 12 -> 2
            speedKmh < 20 -> 3
            speedKmh < 29 -> 4
            speedKmh < 39 -> 5
            speedKmh < 50 -> 6
            speedKmh < 62 -> 7
            speedKmh < 75 -> 8
            else -> 9
        }
        return "${level}级"
    }
}
