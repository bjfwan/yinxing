package com.yinxing.launcher.data.weather.source

import com.yinxing.launcher.data.weather.ChinaRegionIndex
import com.yinxing.launcher.data.weather.WeatherApiClient
import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherHour
import com.yinxing.launcher.data.weather.WeatherNow
import org.json.JSONArray
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

    /**
     * 本地地名索引（懒加载，避免在初始化线程解析 JSON）。命中后直接拿坐标查天气，
     * 跳过 Open-Meteo 地名接口；未命中再走多轮网络补全。
     */
    internal var regionIndexProvider: (() -> ChinaRegionIndex?)? = null

    override suspend fun fetchWeather(cityName: String): WeatherSourceResult? {
        val location = fetchLocation(cityName) ?: return null
        val result = fetchWeather(location.latitude, location.longitude, cityName)
        return location.key?.let { result.copy(locationKey = it) } ?: result
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
        runCatching { regionIndexProvider?.invoke()?.resolve(cityName) }
            .getOrNull()
            ?.let { match ->
                return Location(match.entry.latitude, match.entry.longitude, "adcode:${match.entry.adcode}")
            }
        val merged = JSONArray()
        for (query in candidateQueries(cityName)) {
            val results = searchLocations(query) ?: continue
            for (i in 0 until results.length()) merged.put(results.get(i))
            pickLocation(merged, cityName)
                ?.takeIf { isConfidentLocation(it, cityName) }
                ?.let { return toLocation(it) }
        }
        return pickLocation(merged, cityName)?.let(::toLocation)
    }

    private suspend fun searchLocations(query: String): JSONArray? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=10&language=zh&format=json&countryCode=CN"
        return runCatching { JSONObject(apiClient.get(url)).optJSONArray("results") }.getOrNull()
    }

    private fun toLocation(item: JSONObject): Location {
        val latitude = item.getDouble("latitude")
        val longitude = item.getDouble("longitude")
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "城市坐标无效" }
        return Location(latitude, longitude)
    }

    /**
     * Open-Meteo 地名为精确匹配、不支持模糊搜索：错一个字、少个"区"字、
     * 或"北京朝阳"这类拼接输入都会查不到。生成多轮候选查询——原样、去后缀、
     * 尾部地名、头部上级地名，并给短名补 市/县/区 后缀兜底。
     */
    internal fun candidateQueries(cityName: String): List<String> {
        val input = cityName.trim()
        if (input.isEmpty()) return emptyList()
        val normalized = normalizePlaceName(input)
        val bases = LinkedHashSet<String>()
        bases += input
        if (normalized != input) bases += normalized
        if (normalized.length >= 4) {
            bases += normalized.takeLast(2)
            bases += normalized.take(2)
        }
        val queries = LinkedHashSet<String>()
        for (base in bases) {
            queries += base
            if (base.length <= 3 && normalizePlaceName(base) == base) {
                queries += "${base}市"
                queries += "${base}县"
                queries += "${base}区"
            }
            if (queries.size >= MAX_LOCATION_QUERIES) break
        }
        return queries.take(MAX_LOCATION_QUERIES)
    }

    /** 已找到同名且达到区县/地级级别的结果就不用继续补查了。 */
    internal fun isConfidentLocation(item: JSONObject, query: String): Boolean {
        if (normalizePlaceName(item.optString("name")) != normalizePlaceName(query)) return false
        return when (item.optString("feature_code")) {
            "PPLC", "PPLA", "PPLA2", "PPLA3", "ADM1", "ADM2", "ADM3" -> true
            else -> item.optDouble("population", 0.0) >= CONFIDENT_POPULATION
        }
    }

    /**
     * 从多条候选里选与用户输入最匹配的一条，避免重名地名串到错误坐标。
     * 输入与候选名都先去掉行政区划后缀再比较；无任何文字匹配时返回 null，
     * 宁可报"未找到城市"也不展示错误地点的天气。
     */
    internal fun pickLocation(results: JSONArray, query: String): JSONObject? {
        val normalizedQuery = normalizePlaceName(query)
        if (normalizedQuery.isEmpty()) return null
        var best: JSONObject? = null
        var bestScore = 0.0
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val name = normalizePlaceName(item.optString("name"))
            if (name.isEmpty()) continue
            var score = when {
                name == normalizedQuery -> 100.0
                name.endsWith(normalizedQuery) || normalizedQuery.endsWith(name) -> 60.0
                name.startsWith(normalizedQuery) || normalizedQuery.startsWith(name) -> 60.0
                name.contains(normalizedQuery) || normalizedQuery.contains(name) -> 40.0
                else -> 0.0
            }
            if (score <= 0.0) continue
            val hasAdminInQuery = listOf("admin1", "admin2", "admin3", "admin4")
                .map { normalizePlaceName(item.optString(it)) }
                .any { it.isNotEmpty() && normalizedQuery.contains(it) }
            if (hasAdminInQuery) score += 15.0
            score += item.optDouble("population", 0.0).coerceIn(0.0, 30_000_000.0) / 1_000_000.0
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best
    }

    internal fun normalizePlaceName(name: String): String {
        val trimmed = name.trim()
        val stripped = trimmed.replace(
            Regex("(?:特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|自治州|自治县|地区|盟|省|市|区|县|旗|林区)+$"),
            "",
        )
        return stripped.ifEmpty { trimmed }
    }

    private data class Location(
        val latitude: Double,
        val longitude: Double,
        val key: String? = null,
    )

    private companion object {
        const val MAX_LOCATION_QUERIES = 7
        const val CONFIDENT_POPULATION = 200_000L
    }

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
