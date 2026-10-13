package com.yinxing.launcher.data.weather

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class WeatherLocationInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceLocationResolvesToCity() = runBlocking {
        val result = WeatherLocationResolver.resolve(context)
        Log.i("WeatherIT", "resolve() -> city=${result?.cityName} lat=${result?.latitude} lng=${result?.longitude}")
        assertNotNull("定位应返回城市信息（权限已授予）", result)
        assertTrue(result!!.cityName.isNotBlank())
    }

    @Test
    fun locationPipelineDiagnosis() = runBlocking {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val now = System.currentTimeMillis()
        Log.i("WeatherIT", "allProviders=${manager.allProviders} enabled=${manager.getProviders(true)}")
        manager.allProviders.forEach { p ->
            val loc = runCatching { manager.getLastKnownLocation(p) }.getOrNull()
            Log.i("WeatherIT", "lastKnown[$p] -> ${loc?.latitude},${loc?.longitude} ageMin=${loc?.let { (now - it.time) / 60000 }}")
        }
        val single = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(l: android.location.Location) {
                        if (cont.isActive) cont.resume(l)
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
                    override fun onProviderEnabled(p: String) = Unit
                    override fun onProviderDisabled(p: String) = Unit
                    override fun onFlushComplete(requestCode: Int) = Unit
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, context.mainLooper)
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            }
        }
        Log.i("WeatherIT", "singleUpdate(network) 15s -> ${single?.latitude},${single?.longitude} acc=${single?.accuracy} time=${single?.time}")
        val geocoderOk = runCatching { Geocoder.isPresent() }.getOrDefault(false)
        Log.i("WeatherIT", "Geocoder.isPresent=$geocoderOk")
        if (geocoderOk) {
            val t0 = System.currentTimeMillis()
            val addr = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.SIMPLIFIED_CHINESE).getFromLocation(29.1722, 115.7958, 1)?.firstOrNull()
            }.getOrNull()
            Log.i("WeatherIT", "platformGeocoder(九江) ${System.currentTimeMillis() - t0}ms -> locality=${addr?.locality} admin=${addr?.adminArea} sub=${addr?.subAdminArea} feature=${addr?.featureName}")
        }
        val apiT0 = System.currentTimeMillis()
        val apiBody = runCatching {
            HttpWeatherApiClient().get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=29.1722&longitude=115.7958&localityLanguage=zh")
        }.getOrNull()
        Log.i("WeatherIT", "bigDataCloud(九江) ${System.currentTimeMillis() - apiT0}ms -> ${apiBody?.take(300)}")
        val apiCity = apiBody?.let { runCatching { JSONObject(it).optString("city") }.getOrNull() }
        Log.i("WeatherIT", "bigDataCloud city=$apiCity")
        val resolved = WeatherLocationResolver.resolve(context)
        Log.i("WeatherIT", "resolve() final -> ${resolved?.cityName} ${resolved?.latitude},${resolved?.longitude}")
        Unit
    }

    @Test
    fun wifiPositioningDiagnosis() = runBlocking {
        val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
            as android.telephony.TelephonyManager
        val cells = runCatching { tm.allCellInfo }.getOrNull().orEmpty()
        Log.i("WeatherIT", "allCellInfo count=${cells.size}")
        cells.take(4).forEach { c ->
            Log.i("WeatherIT", "  cell ${c::class.simpleName} registered=${c.isRegistered} id=${c.cellIdentity}")
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as android.net.wifi.WifiManager
        Log.i("WeatherIT", "wifiEnabled=${wifi.isWifiEnabled}")
        var scans = runCatching { wifi.scanResults }.getOrNull().orEmpty()
        Log.i("WeatherIT", "cached scanResults=${scans.size}")
        if (scans.isEmpty()) {
            @Suppress("DEPRECATION")
            val started = runCatching { wifi.startScan() }
            Log.i("WeatherIT", "startScan() -> ${started.getOrNull()} ex=${started.exceptionOrNull()}")
            for (i in 0 until 20) {
                kotlinx.coroutines.delay(500)
                scans = runCatching { wifi.scanResults }.getOrNull().orEmpty()
                if (scans.isNotEmpty()) break
            }
            Log.i("WeatherIT", "after startScan(+10s) scanResults=${scans.size}")
        }
        scans.take(5).forEach { Log.i("WeatherIT", "  ap ${it.BSSID} ${it.level}dBm ${it.SSID}") }
        if (scans.isNotEmpty()) {
            val arr = org.json.JSONArray()
            scans.sortedByDescending { it.level }.take(12).forEach {
                arr.put(JSONObject().put("macAddress", it.BSSID).put("signalStrength", it.level))
            }
            val payload = JSONObject().put("wifiAccessPoints", arr)
                .put("fallbacks", JSONObject().put("ipf", false))
            val body = runCatching {
                HttpWeatherApiClient().post("https://beacondb.net/v1/geolocate", payload.toString())
            }.getOrElse { "ERR:${it.message}" }
            Log.i("WeatherIT", "beaconDB -> ${body.take(400)}")
        }
        Unit
    }

    @Test
    fun regionIndexLoadsFromAssetsAndMatches() {
        val index = ChinaRegionIndex.get(context)
        assertEquals(110105, index.resolve("北京朝阳")?.entry?.adcode)
        assertEquals("朝阳市", index.resolve("朝阳")?.entry?.name)
        assertEquals("杭州市", index.resolve("杭洲")?.entry?.name)
        assertEquals(330106, index.resolve("杭州市西湖区")?.entry?.adcode)
        val nearest = index.nearestCity(31.2304, 121.4737)
        assertEquals("上海市", nearest?.name)
    }

    @Test
    fun cityWeatherUsesLocalIndexThenFetchesForecast() = runBlocking {
        WeatherRepository.initialize(context)
        val state = WeatherRepository.fetchWeather("北京朝阳", context)
        Log.i("WeatherIT", "fetchWeather(北京朝阳) -> ${state::class.simpleName}")
        assertTrue("应返回成功状态，实际=$state", state is WeatherState.Success)
        state as WeatherState.Success
        assertEquals("adcode:110105", state.adcode)
        assertTrue(state.now.temperature > -60 && state.now.temperature < 60)
    }
}
