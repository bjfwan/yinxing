package com.yinxing.launcher.data.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedWeatherLocation(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
)

object WeatherLocationResolver {
    @SuppressLint("MissingPermission")
    suspend fun resolve(context: Context): ResolvedWeatherLocation? {
        val lastKnown = lastKnownLocation(context)
        val location = lastKnown?.takeIf(::isRecent)
            ?: withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { currentLocation(context) }
            ?: lastKnown
            ?: return null
        val city = reverseGeocode(context, location.latitude, location.longitude) ?: "当前位置"
        return ResolvedWeatherLocation(city, location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, { location ->
                    if (continuation.isActive) continuation.resume(location)
                }, null)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
    }

    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
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

    internal fun cityName(address: Address?): String? = address?.locality
        ?: address?.subAdminArea
        ?: address?.adminArea

    internal fun isRecent(
        location: Location,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = nowMs - location.time in 0..RECENT_LOCATION_MAX_AGE_MS

    private const val CURRENT_LOCATION_TIMEOUT_MS = 3_000L
    private const val RECENT_LOCATION_MAX_AGE_MS = 6 * 60 * 60 * 1000L
}
