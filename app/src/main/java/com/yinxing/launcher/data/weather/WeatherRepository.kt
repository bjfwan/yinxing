package com.yinxing.launcher.data.weather

import android.content.Context
import android.os.SystemClock
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceAndReport
import com.yinxing.launcher.common.perf.traceBegin
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.data.weather.source.OpenMeteoWeatherDataSource
import com.yinxing.launcher.data.weather.source.SeniverseWeatherSource
import com.yinxing.launcher.data.weather.source.TencentWeatherSource
import com.yinxing.launcher.data.weather.source.WeatherSource
import com.yinxing.launcher.data.weather.source.WeatherSourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WeatherRepository {
    private const val TAG = "WeatherRepository"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val BACKOFF_FIRST_MS = 60 * 1000L
    private const val BACKOFF_SECOND_MS = 5 * 60 * 1000L
    private const val BACKOFF_MAX_MS = 15 * 60 * 1000L

    private val apiClient = HttpWeatherApiClient()
    private val cacheMutex = Mutex()
    private val inFlightMutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = mutableMapOf<String, Deferred<WeatherState>>()
    private val failureCounts = mutableMapOf<String, Int>()
    private val retryAfterMs = mutableMapOf<String, Long>()

    @Volatile
    private var cache: WeatherState.Success? = null

    @Volatile
    private var diskCache: WeatherDiskCache? = null

    private var weatherSource: WeatherSource = OpenMeteoWeatherDataSource(apiClient)
    private var clock: () -> Long = { System.currentTimeMillis() }

    fun initialize(context: Context) {
        if (diskCache != null) return
        synchronized(this) {
            if (diskCache == null) {
                diskCache = WeatherDiskCache(context)
            }
        }
    }

    suspend fun fetchWeather(cityName: String, context: Context? = null): WeatherState = withContext(Dispatchers.IO) {
        traceBegin(LauncherTraceNames.HOME_WEATHER_REQUEST)
        val startedAt = SystemClock.elapsedRealtime()
        val normalizedCityName = normalizeCityName(cityName)
        val cached = cacheMutex.withLock { freshCached(normalizedCityName) }
        if (cached != null) {
            val result = cached.copy(fromCache = true)
            context?.let { reportWeatherResult(it, result, startedAt) }
            return@withContext result
        }

        backoffState(normalizedCityName)?.let { state ->
            context?.let { reportWeatherResult(it, state, startedAt) }
            return@withContext state
        }

        val deferred = inFlightMutex.withLock {
            inFlightRequests[normalizedCityName]?.takeIf { it.isActive } ?: requestScope.async {
                fetchWeatherInternal(normalizedCityName)
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
        val result = deferred.await()
        context?.let { reportWeatherResult(it, result, startedAt) }
        result
    }

    suspend fun fetchWeatherAtLocation(
        cityName: String,
        latitude: Double,
        longitude: Double,
        context: Context? = null,
    ): WeatherState = withContext(Dispatchers.IO) {
        traceBegin(LauncherTraceNames.HOME_WEATHER_REQUEST)
        val startedAt = SystemClock.elapsedRealtime()
        val normalizedCityName = normalizeCityName(cityName)
        val result = fetchWeatherInternal(normalizedCityName, latitude, longitude)
        context?.let { reportWeatherResult(it, result, startedAt) }
        result
    }

    private fun reportWeatherResult(context: Context, result: WeatherState, startedAt: Long) {
        traceAndReport(context, LauncherTraceNames.HOME_WEATHER_REQUEST)
        LobsterClient.reportUsage(
            context,
            WeatherUsageEventFactory.from(
                state = result,
                durationMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                occurredAt = currentIsoTimestamp()
            )
        )
    }

    private fun currentIsoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    fun clearCache() {
        cache = null
        diskCache?.clear()
        synchronized(this) {
            failureCounts.clear()
            retryAfterMs.clear()
        }
    }

    fun getCached(): WeatherState? {
        val memory = cache
        if (memory != null) return memory
        val disk = diskCache?.read() ?: return null
        cache = disk.copy(fromCache = false)
        return disk
    }

    private fun normalizeCityName(cityName: String): String {
        return cityName.trim().ifEmpty { "北京" }
    }

    private fun freshCached(cityName: String): WeatherState.Success? {
        val cached = cachedSuccess(cityName) ?: return null
        return cached.takeIf {
            clock() - it.lastFetchTime < CACHE_TTL_MS &&
                it.forecast.size >= 4 &&
                it.hourly.isNotEmpty()
        }
    }

    private fun cachedSuccess(cityName: String): WeatherState.Success? {
        val memory = cache
        if (memory != null && memory.cityName == cityName) return memory
        val disk = diskCache?.read(cityName) ?: return null
        cache = disk.copy(fromCache = false)
        return disk
    }

    private fun backoffState(cityName: String): WeatherState? {
        val retryAt = synchronized(this) { retryAfterMs[cityName] } ?: return null
        if (clock() >= retryAt) return null
        val cached = cachedSuccess(cityName)?.copy(fromCache = true)
        val message = "天气请求稍后重试"
        return if (cached != null) {
            WeatherState.UsingCache(cached, WeatherFailureReason.Backoff, message)
        } else {
            WeatherState.Failure(cityName, WeatherFailureReason.Backoff, message)
        }
    }

    private suspend fun fetchWeatherInternal(
        cityName: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): WeatherState {
        return try {
            val weather = if (latitude != null && longitude != null) {
                weatherSource.fetchWeather(latitude, longitude, cityName)
            } else {
                weatherSource.fetchWeather(cityName)
            }
            if (weather == null) {
                val cached = cachedSuccess(cityName)?.copy(fromCache = true)
                return if (cached != null) {
                    WeatherState.UsingCache(
                        cached,
                        WeatherFailureReason.Api,
                        "城市查询暂时不可用",
                    )
                } else {
                    WeatherState.CityNotFound(cityName)
                }
            }
            val state = WeatherState.Success(
                cityName = cityName,
                adcode = weather.locationKey,
                now = weather.now,
                forecast = weather.forecast,
                hourly = weather.hourly,
                lastFetchTime = clock()
            )
            cacheMutex.withLock {
                cache = state
                diskCache?.write(state)
                recordSuccess(cityName)
            }
            state
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w(TAG, "fetchWeatherInternal failed for $cityName", e)
            val reason = classifyFailure(e)
            val message = e.message ?: "网络请求失败"
            recordFailure(cityName)
            val cached = cachedSuccess(cityName)?.copy(fromCache = true)
            if (cached != null) {
                WeatherState.UsingCache(cached, reason, message)
            } else {
                WeatherState.Failure(cityName, reason, message)
            }
        }
    }

    private fun classifyFailure(error: Exception): WeatherFailureReason {
        return when (error) {
            is IOException -> WeatherFailureReason.Network
            is JSONException -> WeatherFailureReason.Parse
            is IllegalStateException -> WeatherFailureReason.Api
            else -> WeatherFailureReason.Unknown
        }
    }

    private fun recordSuccess(cityName: String) {
        synchronized(this) {
            failureCounts.remove(cityName)
            retryAfterMs.remove(cityName)
        }
    }

    private fun recordFailure(cityName: String) {
        synchronized(this) {
            val count = (failureCounts[cityName] ?: 0) + 1
            failureCounts[cityName] = count
            retryAfterMs[cityName] = clock() + backoffMs(count)
        }
    }

    private fun backoffMs(failureCount: Int): Long {
        return when (failureCount) {
            1 -> BACKOFF_FIRST_MS
            2 -> BACKOFF_SECOND_MS
            else -> BACKOFF_MAX_MS
        }
    }

    internal fun configureForTest(
        tencentSource: TencentWeatherSource,
        seniverseSource: SeniverseWeatherSource,
        diskCache: WeatherDiskCache?,
        clock: () -> Long
    ) {
        this.weatherSource = object : WeatherSource {
            override suspend fun fetchWeather(cityName: String): WeatherSourceResult? {
                val adcode = tencentSource.searchAdcode(cityName) ?: return null
                val (now, forecast) = coroutineScope {
                    val nowDeferred = async { tencentSource.fetchNow(adcode, cityName) }
                    val forecastDeferred = async { seniverseSource.fetchForecast(cityName) }
                    nowDeferred.await() to forecastDeferred.await()
                }
                return WeatherSourceResult(
                    locationKey = adcode,
                    now = now ?: error("实时天气为空"),
                    forecast = forecast,
                    hourly = emptyList()
                )
            }
        }
        this.diskCache = diskCache
        this.clock = clock
        clearRuntimeStateForTest()
    }

    internal fun clearMemoryCacheForTest() {
        cache = null
    }

    internal fun clearRuntimeStateForTest() {
        cache = null
        synchronized(this) {
            inFlightRequests.clear()
            failureCounts.clear()
            retryAfterMs.clear()
        }
    }

    internal fun resetForTest() {
        weatherSource = OpenMeteoWeatherDataSource(apiClient)
        diskCache = null
        clock = { System.currentTimeMillis() }
        clearRuntimeStateForTest()
    }
}
