package com.yinxing.launcher.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class ResolvedWeatherLocation(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
)

object WeatherLocationResolver {
    internal const val FALLBACK_CITY_NAME = "当前位置"

    private val apiClient: WeatherApiClient = HttpWeatherApiClient()

    suspend fun resolve(context: Context): ResolvedWeatherLocation? {
        val lastKnown = lastKnownLocation(context)
        val location = lastKnown?.takeIf(::isRecent)
            ?: withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { currentLocation(context) }
            ?: lastKnown
        if (location != null) {
            val city = reverseGeocode(context, location.latitude, location.longitude)
                ?: FALLBACK_CITY_NAME
            return ResolvedWeatherLocation(city, location.latitude, location.longitude)
        }
        return ipLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val providers = manager?.let { enabledLocationProviders(it, context) }.orEmpty()
        if (manager == null || providers.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val pending = AtomicInteger(providers.size)
        val lock = Any()
        val signals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()

        fun cleanup() {
            synchronized(lock) {
                signals.forEach { runCatching { it.cancel() } }
                signals.clear()
                listeners.forEach { runCatching { manager.removeUpdates(it) } }
                listeners.clear()
            }
        }

        fun complete(location: Location?) {
            if (location == null && pending.decrementAndGet() > 0) return
            cleanup()
            if (continuation.isActive) continuation.resume(location)
        }

        continuation.invokeOnCancellation { cleanup() }
        providers.forEach { provider ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    synchronized(lock) { signals += signal }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        complete(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { complete(location) }
                        }

                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) = Unit

                        /**
                         * MIUI 的 Android 10 框架把 onFlushComplete 改成了接口的抽象方法
                         * （AOSP 直到 API 31 才加入且是 default）。不覆写时 ListenerTransport
                         * 回调会抛 AbstractMethodError 直接崩掉进程（小米 MI 8 实测）。
                         */
                        override fun onFlushComplete(requestCode: Int) = Unit
                    }
                    synchronized(lock) { listeners += listener }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, context.mainLooper)
                }
            }.onFailure { complete(null) }
        }
    }

    /**
     * GPS_PROVIDER 在 Android 11 及以下需要 ACCESS_FINE_LOCATION，只有粗略权限时
     * 直接抛 SecurityException；Android 12+ 则会返回模糊坐标，可以使用。
     */
    internal fun enabledLocationProviders(manager: LocationManager, context: Context): List<String> {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val gpsAllowed = fineGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return enabled.filter {
            it == LocationManager.NETWORK_PROVIDER ||
                (gpsAllowed && it == LocationManager.GPS_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { manager.allProviders }.getOrDefault(emptyList())
        return providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
    }

    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        platformCityName(context, latitude, longitude)?.let { return it }
        apiCityName(latitude, longitude)?.let { return it }
        // 离线兜底：本地行政区索引找最近城市名
        return runCatching {
            ChinaRegionIndex.get(context).nearestCity(latitude, longitude)?.city
        }.getOrNull()
    }

    private suspend fun platformCityName(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return cityName(address)
    }

    /** 国内 ROM 大多没有 Geocoder 后端，用免 key 的逆地理接口兜底。 */
    private suspend fun apiCityName(latitude: Double, longitude: Double): String? {
        val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
            "?latitude=$latitude&longitude=$longitude&localityLanguage=zh"
        val body = runCatching { apiClient.get(url) }.getOrNull() ?: return null
        return runCatching { apiCityName(JSONObject(body)) }.getOrNull()
    }

    internal fun apiCityName(root: JSONObject): String? = listOf(
        root.optString("city"),
        root.optString("locality"),
        root.optString("principalSubdivision"),
    ).map { it.trim() }.firstOrNull { it.isNotEmpty() }

    /** 系统定位彻底失败时的最后兜底：IP 粗定位到城市级。 */
    private suspend fun ipLocation(): ResolvedWeatherLocation? {
        val url = "https://ipwho.is/?lang=zh-CN&fields=success,city,region,latitude,longitude"
        val body = runCatching { apiClient.get(url) }.getOrNull() ?: return null
        return runCatching { ipLocation(JSONObject(body)) }.getOrNull()
    }

    internal fun ipLocation(root: JSONObject): ResolvedWeatherLocation? {
        if (!root.optBoolean("success", false)) return null
        val latitude = root.optDouble("latitude", Double.NaN)
        val longitude = root.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val city = listOf(root.optString("city"), root.optString("region"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: FALLBACK_CITY_NAME
        return ResolvedWeatherLocation(city, latitude, longitude)
    }

    internal fun cityName(address: Address?): String? = address?.locality
        ?: address?.subAdminArea
        ?: address?.adminArea

    internal fun isRecent(
        location: Location,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = nowMs - location.time in 0..RECENT_LOCATION_MAX_AGE_MS

    private const val CURRENT_LOCATION_TIMEOUT_MS = 10_000L
    private const val RECENT_LOCATION_MAX_AGE_MS = 6 * 60 * 60 * 1000L
}
