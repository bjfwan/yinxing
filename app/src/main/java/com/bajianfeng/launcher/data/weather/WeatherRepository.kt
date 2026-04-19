package com.bajianfeng.launcher.data.weather

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WeatherRepository {

    // 心知天气（从 BuildConfig 注入，密钥在 local.properties 中配置）
    private const val SENIVERSE_UID = BuildConfig.SENIVERSE_UID
    private const val SENIVERSE_PK = BuildConfig.SENIVERSE_PK

    // 腾讯位置（从 BuildConfig 注入，密钥在 local.properties 中配置）
    private const val TENCENT_KEY = BuildConfig.TENCENT_KEY

    // 缓存刷新间隔：30分钟
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    @Volatile
    private var cache: WeatherState? = null

    /** 外部调用入口：传城市名，返回完整天气状态 */
    suspend fun fetchWeather(cityName: String): WeatherState = withContext(Dispatchers.IO) {
        val cached = cache
        if (cached != null
            && cached.cityName == cityName
            && cached.isValid
            && System.currentTimeMillis() - cached.lastFetchTime < CACHE_TTL_MS
        ) {
            return@withContext cached
        }

        return@withContext try {
            // Step1: 腾讯行政区划搜索 → adcode
            val adcode = searchAdcode(cityName)
                ?: return@withContext WeatherState.empty().copy(
                    cityName = cityName,
                    error = "未找到城市"
                )

            // Step2: 并行请求实况 + 预报
            val now = fetchTencentNow(adcode, cityName)
            val forecast = fetchSeniverseForecast(cityName)

            val state = WeatherState(
                cityName = cityName,
                adcode = adcode,
                now = now,
                forecast = forecast,
                lastFetchTime = System.currentTimeMillis()
            )
            cache = state
            state
        } catch (e: Exception) {
            WeatherState.empty().copy(
                cityName = cityName,
                error = e.message ?: "网络请求失败"
            )
        }
    }

    fun clearCache() {
        cache = null
    }

    fun getCached(): WeatherState? = cache

    // ──────────────────────────────────────────────
    // 腾讯位置：行政区划搜索 → adcode
    // ──────────────────────────────────────────────
    private fun searchAdcode(cityName: String): String? {
        val encoded = URLEncoder.encode(cityName, "UTF-8")
        val url = "https://apis.map.qq.com/ws/district/v1/search" +
                "?keyword=$encoded&key=$TENCENT_KEY"
        val json = httpGet(url)
        val root = JSONObject(json)
        if (root.getInt("status") != 0) return null
        val resultArr = root.getJSONArray("result")
        if (resultArr.length() == 0) return null
        val firstGroup = resultArr.getJSONArray(0)
        if (firstGroup.length() == 0) return null
        // 取 level<=2（省/市级）的第一个结果
        for (i in 0 until firstGroup.length()) {
            val item = firstGroup.getJSONObject(i)
            val level = item.optInt("level", 99)
            if (level <= 2) {
                return item.getString("id")
            }
        }
        return firstGroup.getJSONObject(0).getString("id")
    }

    // ──────────────────────────────────────────────
    // 腾讯位置：实况天气
    // ──────────────────────────────────────────────
    private fun fetchTencentNow(adcode: String, cityName: String): WeatherNow? {
        val url = "https://apis.map.qq.com/ws/weather/v1/" +
                "?adcode=$adcode&key=$TENCENT_KEY"
        val json = httpGet(url)
        val root = JSONObject(json)
        if (root.getInt("status") != 0) return null
        val realtimeArr = root.getJSONObject("result").getJSONArray("realtime")
        if (realtimeArr.length() == 0) return null
        val item = realtimeArr.getJSONObject(0)
        val infos = item.getJSONObject("infos")
        val updateTime = item.optString("update_time", "")
            .let { if (it.length >= 16) it.substring(11, 16) else it }
        return WeatherNow(
            cityName = cityName,
            weather = infos.optString("weather", ""),
            temperature = infos.optInt("temperature", 0),
            windDirection = infos.optString("wind_direction", ""),
            windPower = infos.optString("wind_power", ""),
            humidity = infos.optInt("humidity", 0),
            updateTime = updateTime
        )
    }

    // ──────────────────────────────────────────────
    // 心知天气：3天预报
    // ──────────────────────────────────────────────
    private fun fetchSeniverseForecast(cityName: String): List<WeatherForecastDay> {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val paramStr = "ts=$ts&uid=$SENIVERSE_UID"
        val sig = hmacSha1Base64(SENIVERSE_PK, paramStr)
        val sigEncoded = URLEncoder.encode(sig, "UTF-8")
        val locationEncoded = URLEncoder.encode(cityName, "UTF-8")
        val url = "https://api.seniverse.com/v3/weather/daily.json" +
                "?ts=$ts&uid=$SENIVERSE_UID&sig=$sigEncoded" +
                "&location=$locationEncoded&language=zh-Hans&unit=c&start=0&days=3"
        val json = httpGet(url)
        val root = JSONObject(json)
        val results = root.getJSONArray("results")
        if (results.length() == 0) return emptyList()
        val daily = results.getJSONObject(0).getJSONArray("daily")
        val list = mutableListOf<WeatherForecastDay>()
        for (i in 0 until daily.length()) {
            val d = daily.getJSONObject(i)
            list.add(
                WeatherForecastDay(
                    date = d.optString("date", ""),
                    textDay = d.optString("text_day", ""),
                    textNight = d.optString("text_night", ""),
                    high = d.optString("high", "0").toIntOrNull() ?: 0,
                    low = d.optString("low", "0").toIntOrNull() ?: 0,
                    weatherCode = d.optString("code_day", "0")
                )
            )
        }
        return list
    }

    // ──────────────────────────────────────────────
    // 工具：HMAC-SHA1 + Base64
    // ──────────────────────────────────────────────
    private fun hmacSha1Base64(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ──────────────────────────────────────────────
    // 工具：HTTP GET
    // ──────────────────────────────────────────────
    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
