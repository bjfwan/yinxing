package com.yinxing.launcher.data.weather

import android.util.Base64
import android.util.Log
import com.yinxing.launcher.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WeatherRepository {
    private const val TAG = "WeatherRepository"
    private const val SENIVERSE_UID = BuildConfig.SENIVERSE_UID
    private const val SENIVERSE_PK = BuildConfig.SENIVERSE_PK
    private const val TENCENT_KEY = BuildConfig.TENCENT_KEY
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val cacheMutex = Mutex()
    private val inFlightMutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = mutableMapOf<String, Deferred<WeatherState>>()
    @Volatile private var cache: WeatherState? = null

    suspend fun fetchWeather(cityName: String): WeatherState = withContext(Dispatchers.IO) {
        val normalizedCityName = normalizeCityName(cityName)
        cacheMutex.withLock {
            freshCached(normalizedCityName)?.let { cached ->
                return@withContext cached
            }
        }

        val deferred = inFlightMutex.withLock {
            inFlightRequests[normalizedCityName]?.takeIf { it.isActive } ?: requestScope.async {
                runCatching { fetchWeatherInternal(normalizedCityName) }
                    .getOrElse { failure ->
                        if (failure is CancellationException) throw failure
                        Log.w(TAG, "fetchWeatherInternal failed for $normalizedCityName", failure)
                        WeatherState.empty().copy(
                            cityName = normalizedCityName,
                            error = failure.message ?: "网络请求失败"
                        )
                    }
            }.also { request ->
                inFlightRequests[normalizedCityName] = request
                request.invokeOnCompletion {
                    requestScope.launch {
                        inFlightMutex.withLock {
                            if (inFlightRequests[normalizedCityName] === request) {
                                inFlightRequests.remove(normalizedCityName)
                            }
                        }
                    }
                }
            }
        }
        deferred.await()
    }

    fun clearCache() {
        cache = null
    }

    fun getCached(): WeatherState? = cache

    private fun normalizeCityName(cityName: String): String {
        return cityName.trim().ifEmpty { "北京" }
    }

    private fun freshCached(cityName: String): WeatherState? {
        val cached = cache ?: return null
        return cached.takeIf {
            it.cityName == cityName &&
                it.isValid &&
                System.currentTimeMillis() - it.lastFetchTime < CACHE_TTL_MS
        }
    }

    private suspend fun fetchWeatherInternal(cityName: String): WeatherState {
        return try {
            val adcode = searchAdcode(cityName)
                ?: return WeatherState.empty().copy(
                    cityName = cityName,
                    error = "未找到城市"
                )

            val (now, forecast) = coroutineScope {
                val nowDeferred = async { fetchTencentNow(adcode, cityName) }
                val forecastDeferred = async { fetchSeniverseForecast(cityName) }
                nowDeferred.await() to forecastDeferred.await()
            }

            val state = WeatherState(
                cityName = cityName,
                adcode = adcode,
                now = now,
                forecast = forecast,
                lastFetchTime = System.currentTimeMillis()
            )
            cacheMutex.withLock { cache = state }
            state
        } catch (e: Exception) {
            WeatherState.empty().copy(
                cityName = cityName,
                error = e.message ?: "网络请求失败"
            )
        }
    }

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
        for (i in 0 until firstGroup.length()) {
            val item = firstGroup.getJSONObject(i)
            val level = item.optInt("level", 99)
            if (level <= 2) {
                return item.getString("id")
            }
        }
        return firstGroup.getJSONObject(0).getString("id")
    }

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

    private fun hmacSha1Base64(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val body = stream.use { it.bufferedReader().readText() }
            check(responseCode in 200..299) { "HTTP $responseCode" }
            body
        } finally {
            conn.disconnect()
        }
    }
}
