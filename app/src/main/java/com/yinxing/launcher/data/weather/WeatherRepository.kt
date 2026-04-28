package com.yinxing.launcher.data.weather

import android.content.Context
import android.util.Log
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceSection
import com.yinxing.launcher.data.weather.source.SeniverseWeatherDataSource
import com.yinxing.launcher.data.weather.source.SeniverseWeatherSource
import com.yinxing.launcher.data.weather.source.TencentWeatherDataSource
import com.yinxing.launcher.data.weather.source.TencentWeatherSource
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

    private var tencentSource: TencentWeatherSource = TencentWeatherDataSource(apiClient)
    private var seniverseSource: SeniverseWeatherSource = SeniverseWeatherDataSource(apiClient)
    private var clock: () -> Long = { System.currentTimeMillis() }

    fun initialize(context: Context) {
        if (diskCache != null) return
        synchronized(this) {
            if (diskCache == null) {
                diskCache = WeatherDiskCache(context)
            }
        }
    }

    suspend fun fetchWeather(cityName: String): WeatherState = withContext(Dispatchers.IO) {
        traceSection(LauncherTraceNames.HOME_WEATHER_REQUEST) {
            val normalizedCityName = normalizeCityName(cityName)
            cacheMutex.withLock {
                freshCached(normalizedCityName)?.let { cached ->
                    return@traceSection cached.copy(fromCache = true)
                }
            }

            backoffState(normalizedCityName)?.let { state ->
                return@traceSection state
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
            deferred.await()
        }
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
        return cached.takeIf { clock() - it.lastFetchTime < CACHE_TTL_MS }
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

    private suspend fun fetchWeatherInternal(cityName: String): WeatherState {
        return try {
            val adcode = tencentSource.searchAdcode(cityName)
                ?: return WeatherState.CityNotFound(cityName)

            val (now, forecast) = coroutineScope {
                val nowDeferred = async { tencentSource.fetchNow(adcode, cityName) }
                val forecastDeferred = async { seniverseSource.fetchForecast(cityName) }
                nowDeferred.await() to forecastDeferred.await()
            }

            val currentNow = now ?: error("实时天气为空")
            val state = WeatherState.Success(
                cityName = cityName,
                adcode = adcode,
                now = currentNow,
                forecast = forecast,
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
            Log.w(TAG, "fetchWeatherInternal failed for $cityName", e)
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
        this.tencentSource = tencentSource
        this.seniverseSource = seniverseSource
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
        tencentSource = TencentWeatherDataSource(apiClient)
        seniverseSource = SeniverseWeatherDataSource(apiClient)
        diskCache = null
        clock = { System.currentTimeMillis() }
        clearRuntimeStateForTest()
    }
}
