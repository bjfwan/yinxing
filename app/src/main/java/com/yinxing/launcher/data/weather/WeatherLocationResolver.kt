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
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.data.home.LauncherPreferences
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class ResolvedWeatherLocation(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    /** true = 近似定位（IP 粗定位或过期的设备缓存坐标），城市可能有偏差。 */
    val approximate: Boolean = false,
    /** 定位来源：provider/radio/ip/cache_fresh/cache_stale，用于命中率统计。 */
    val source: String = "",
)

object WeatherLocationResolver {
    internal const val FALLBACK_CITY_NAME = "当前位置"

    private val apiClient = HttpWeatherApiClient()

    /**
     * 三条路线并行发起，按精度优先级取结果：
     * 系统 provider > WiFi/基站指纹（beaconDB）> IP 粗定位 > 过期缓存坐标。
     * 串行执行会让用户在“正在定位”上等十几秒；并行后总耗时≈最慢的一路，
     * 且任何一路先出结果都能立刻收敛。
     */
    suspend fun resolve(context: Context): ResolvedWeatherLocation? {
        val startedAt = SystemClock.elapsedRealtime()
        val result = runCatching { resolveInternal(context) }.getOrNull()
        reportResolution(context, result, SystemClock.elapsedRealtime() - startedAt)
        return result
    }

    private suspend fun resolveInternal(context: Context): ResolvedWeatherLocation? {
        val lastKnown = lastKnownLocation(context)
        lastKnown?.takeIf(::isRecent)?.let { return located(context, it, source = SOURCE_CACHE_FRESH) }
        return coroutineScope {
            val providerJob = async {
                withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { currentLocation(context) }
            }
            val radioJob = async { runCatching { radioLocation(context) }.getOrNull() }
            val ipJob = async { runCatching { ipLocation() }.getOrNull() }

            // 指纹定位通常 2~5s 收敛（成功或失败都很快）。它先回来时
            // 只给 provider 留一个短宽限——城市级天气不需要为更准的
            // 坐标让用户多等七八秒；它失败时才把 provider 等满，
            // 因为 GPS 可能是最后的精确来源。
            val radio = radioJob.await()
            val pair = if (radio != null) {
                (withTimeoutOrNull(PROVIDER_GRACE_MS) { providerJob.await() } to SOURCE_PROVIDER)
                    .takeIf { it.first != null } ?: (radio to SOURCE_RADIO)
            } else {
                providerJob.await()?.let { it to SOURCE_PROVIDER } ?: (null to "")
            }
            val (location, source) = pair
            if (location != null) {
                providerJob.cancel()
                ipJob.cancel()
                if (source == SOURCE_PROVIDER) maybeSubmitObservation(context, location)
                return@coroutineScope located(context, location, source = source)
            }
            providerJob.cancel()
            ipJob.await()
                ?: lastKnown?.let {
                    located(context, it, approximate = true, source = SOURCE_CACHE_STALE)
                }
        }
    }

    private suspend fun located(
        context: Context,
        location: Location,
        approximate: Boolean = false,
        source: String = "",
    ): ResolvedWeatherLocation {
        val city = reverseGeocode(context, location.latitude, location.longitude)
            ?: FALLBACK_CITY_NAME
        return ResolvedWeatherLocation(city, location.latitude, location.longitude, approximate, source)
    }

    /** 只上报定位来源与耗时（不含坐标和城市名），用于统计指纹库命中率。 */
    private fun reportResolution(
        context: Context,
        result: ResolvedWeatherLocation?,
        durationMs: Long,
    ) {
        runCatching {
            LobsterClient.reportUsage(
                context,
                WeatherUsageEventFactory.locationResolved(
                    source = result?.source ?: "none",
                    durationMs = durationMs.coerceAtLeast(0L),
                    approximate = result?.approximate ?: false,
                    occurredAt = isoNow(),
                )
            )
        }
    }

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

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
                    // vivo/MIUI 等 ROM 的 requestSingleUpdate 经常不回调，
                    // 补一个常规更新请求兜底：首个回调即完成，cleanup 会统一注销。
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                    }
                }
            }.onFailure { complete(null) }
        }
    }

    /**
     * GPS_PROVIDER 在 Android 11 及以下需要 ACCESS_FINE_LOCATION，只有粗略权限时
     * 直接抛 SecurityException；Android 12+ 则会返回模糊坐标，可以使用。
     * 注意不能用 getProviders(true)：GPS 这类 provider 平时显示为 disabled，
     * 有权限的 app 发起请求后系统才会启用；fused/passive 同理要先请求才会应答。
     */
    internal fun enabledLocationProviders(manager: LocationManager, context: Context): List<String> {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val gpsAllowed = fineGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val available = runCatching { manager.allProviders }.getOrDefault(emptyList())
        return available.filter {
            it == LocationManager.NETWORK_PROVIDER ||
                it == LocationManager.FUSED_PROVIDER ||
                it == LocationManager.PASSIVE_PROVIDER ||
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
        // 部分国产 ROM 的 Geocoder 后端会长时间阻塞，必须加超时，否则“正在定位”一直转圈。
        val address = withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    /**
     * 系统定位 provider 无响应时的无线指纹定位：把附近 WiFi AP 和蜂窝基站
     * 一起交给免 key 的 beaconDB（MLS 兼容协议）解算坐标。
     * 对国产 ROM 很关键——它们大多没有可用的 network provider 后端，
     * 但 modem 即使无卡/无服务也通常能看到基站，WiFi 也大多能扫到 AP。
     * 再往后只剩 IP 粗定位，运营商出口位置经常把整个用户定到省会/北京。
     */
    @SuppressLint("MissingPermission")
    private suspend fun radioLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val cells = cellTowers(context)
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        var aps = wifi?.let(::collectAps).orEmpty()
        if (aps.isEmpty() && wifi != null) {
            @Suppress("DEPRECATION")
            runCatching { wifi.startScan() }
            for (i in 0 until 5) {
                delay(500)
                aps = collectAps(wifi)
                if (aps.isNotEmpty()) break
            }
        }
        if (aps.isEmpty() && cells.length() == 0) return null

        val apArray = JSONArray()
        aps.take(12).forEach { (bssid, level) ->
            apArray.put(
                JSONObject()
                    .put("macAddress", bssid)
                    .put("signalStrength", level)
            )
        }
        val payload = JSONObject()
            .put("wifiAccessPoints", apArray)
            .put("cellTowers", cells)
            .put("fallbacks", JSONObject().put("ipf", false).put("lacf", true))
        val body = runCatching {
            apiClient.post("https://beacondb.net/v1/geolocate", payload.toString())
        }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(body)
            if (root.optString("fallback").isNotEmpty()) return null
            val loc = root.getJSONObject("location")
            Location(RADIO_LOCATION_SOURCE).apply {
                latitude = loc.getDouble("lat")
                longitude = loc.getDouble("lng")
                accuracy = root.optDouble("accuracy", 500.0).toFloat()
                time = System.currentTimeMillis()
            }
        }.getOrNull()
    }

    private fun collectAps(wifi: WifiManager): List<Pair<String, Int>> =
        runCatching { wifi.scanResults }.getOrNull().orEmpty()
            .asSequence()
            .mapNotNull { it.BSSID?.takeIf(String::isNotBlank)?.to(it.level) }
            .sortedByDescending { it.second }
            .toList()

    private fun apsToJson(aps: List<Pair<String, Int>>): JSONArray {
        val array = JSONArray()
        aps.take(12).forEach { (bssid, level) ->
            array.put(
                JSONObject()
                    .put("macAddress", bssid)
                    .put("signalStrength", level)
            )
        }
        return array
    }

    /**
     * 用户开启“定位数据贡献”后，把真实 provider 定位与其同时看到的
     * WiFi/基站指纹一起匿名上报到 beaconDB（MLS geosubmit 协议，公共领域数据）。
     * 只有真实定位结果才有资格成为观测样本——缓存坐标和 IP 结果不上报，
     * 否则会把自己的粗定位写进公共库。fire-and-forget，不影响定位耗时。
     */
    private fun maybeSubmitObservation(context: Context, location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_OBSERVATION_ACCURACY_M) return
        val enabled = runCatching {
            LauncherPreferences.getInstance(context).isLocationSharingEnabled()
        }.getOrDefault(false)
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        synchronized(submitLock) {
            if (now - lastSubmitElapsedMs < MIN_SUBMIT_INTERVAL_MS) return
            lastSubmitElapsedMs = now
        }
        submitScope.launch { submitObservation(context, location) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun submitObservation(context: Context, location: Location) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cells = cellTowers(context)
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val aps = wifi?.let(::collectAps).orEmpty()
        if (cells.length() == 0 && aps.isEmpty()) return
        val item = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put(
                "location",
                JSONObject()
                    .put("lat", location.latitude)
                    .put("lng", location.longitude)
                    .put(
                        "accuracy",
                        if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL
                    )
            )
            .put("cellTowers", cells)
            .put("wifiAccessPoints", apsToJson(aps))
        val payload = JSONObject().put("items", JSONArray().put(item))
        runCatching {
            apiClient.post("https://beacondb.net/v2/geosubmit", payload.toString())
        }
    }

    /**
     * 读取 modem 当前可见的蜂窝小区（LTE/WCDMA/NR），转成 MLS cellTowers 数组。
     * 无 SIM 服务时 modem 也常上报驻留小区（本机 vivo 实测 OUT_OF_SERVICE
     * 仍返回 CellIdentityLte）。仅需要 ACCESS_FINE_LOCATION。
     */
    @SuppressLint("MissingPermission")
    private fun cellTowers(context: Context): JSONArray {
        val telephony = context.applicationContext
            .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return JSONArray()
        val infos = runCatching { telephony.allCellInfo }.getOrNull().orEmpty()
        val array = JSONArray()
        infos.forEach { info ->
            val tower = when (info) {
                is CellInfoLte -> info.cellIdentity.let { id ->
                    cellTower("lte", id.mccCompat(), id.mncCompat(), id.tac, id.ci.toLong())
                }
                is CellInfoWcdma -> info.cellIdentity.let { id ->
                    cellTower("wcdma", id.mccCompat(), id.mncCompat(), id.lac, id.cid.toLong())
                }
                is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (info.cellIdentity as? CellIdentityNr)?.let { id ->
                        cellTower("nr", id.mccString?.toIntOrNull(), id.mncString?.toIntOrNull(), id.tac, id.nci)
                    }
                } else {
                    null
                }
                else -> null
            }
            if (tower != null) array.put(tower)
        }
        return array
    }

    /** 没有有效 MCC/MNC 的小区对定位无用，直接丢弃。 */
    internal fun cellTower(
        radioType: String,
        mcc: Int?,
        mnc: Int?,
        areaCode: Int,
        cellId: Long,
    ): JSONObject? {
        if (mcc == null || mnc == null || areaCode <= 0 || cellId <= 0) return null
        return JSONObject()
            .put("radioType", radioType)
            .put("mobileCountryCode", mcc)
            .put("mobileNetworkCode", mnc)
            .put("locationAreaCode", areaCode)
            .put("cellId", cellId)
    }

    /** API 28 前后 MCC/MNC 的取值方式不同：旧版 int 用 MAX_VALUE 表示未知。 */
    private fun CellIdentityLte.mccCompat(): Int? = compatMcc(mccStringOrNull(), rawMcc())
    private fun CellIdentityLte.mncCompat(): Int? = compatMnc(mncStringOrNull(), rawMnc())
    private fun CellIdentityWcdma.mccCompat(): Int? = compatMcc(mccStringOrNull(), rawMcc())
    private fun CellIdentityWcdma.mncCompat(): Int? = compatMnc(mncStringOrNull(), rawMnc())

    private fun CellIdentityLte.mccStringOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mccString else null

    private fun CellIdentityLte.mncStringOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mncString else null

    private fun CellIdentityWcdma.mccStringOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mccString else null

    private fun CellIdentityWcdma.mncStringOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mncString else null

    @Suppress("DEPRECATION")
    private fun CellIdentityLte.rawMcc(): Int = mcc

    @Suppress("DEPRECATION")
    private fun CellIdentityLte.rawMnc(): Int = mnc

    @Suppress("DEPRECATION")
    private fun CellIdentityWcdma.rawMcc(): Int = mcc

    @Suppress("DEPRECATION")
    private fun CellIdentityWcdma.rawMnc(): Int = mnc

    private fun compatMcc(stringValue: String?, rawValue: Int): Int? =
        stringValue?.toIntOrNull() ?: rawValue.takeIf { it > 0 && it != Int.MAX_VALUE }

    private fun compatMnc(stringValue: String?, rawValue: Int): Int? =
        stringValue?.toIntOrNull() ?: rawValue.takeIf { it >= 0 && it != Int.MAX_VALUE }

    /** 系统定位彻底失败时的兜底：IP 粗定位到城市级，两个免 key 源互为备份。 */
    private suspend fun ipLocation(): ResolvedWeatherLocation? {
        val primary = runCatching {
            apiClient.get(
                "https://ipwho.is/?lang=zh-CN&fields=success,city,region,latitude,longitude"
            )
        }.getOrNull()
        runCatching { ipLocation(JSONObject(primary ?: "")) }.getOrNull()?.let { return it }

        val secondary = runCatching { apiClient.get("https://api.ip.sb/geoip") }.getOrNull()
        return runCatching { ipSbLocation(JSONObject(secondary ?: "")) }.getOrNull()
    }

    /** api.ip.sb 响应：city/region 为英文名，无 success 字段，靠坐标有效性判断。 */
    internal fun ipSbLocation(root: JSONObject): ResolvedWeatherLocation? {
        val latitude = root.optDouble("latitude", Double.NaN)
        val longitude = root.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val city = listOf(root.optString("city"), root.optString("region"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: FALLBACK_CITY_NAME
        return ResolvedWeatherLocation(city, latitude, longitude, approximate = true, source = SOURCE_IP)
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
        return ResolvedWeatherLocation(city, latitude, longitude, approximate = true, source = SOURCE_IP)
    }

    internal fun cityName(address: Address?): String? = address?.locality
        ?: address?.subAdminArea
        ?: address?.adminArea

    internal fun isRecent(
        location: Location,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = nowMs - location.time in 0..RECENT_LOCATION_MAX_AGE_MS

    private const val RADIO_LOCATION_SOURCE = "radio"
    private const val PROVIDER_GRACE_MS = 2_000L
    private const val SOURCE_PROVIDER = "provider"
    private const val SOURCE_RADIO = "radio"
    private const val SOURCE_IP = "ip"
    private const val SOURCE_CACHE_FRESH = "cache_fresh"
    private const val SOURCE_CACHE_STALE = "cache_stale"
    private const val MIN_SUBMIT_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_OBSERVATION_ACCURACY_M = 2000f
    private val submitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val submitLock = Any()
    @Volatile private var lastSubmitElapsedMs = 0L
    private const val CURRENT_LOCATION_TIMEOUT_MS = 10_000L
    private const val GEOCODER_TIMEOUT_MS = 6_000L
    private const val RECENT_LOCATION_MAX_AGE_MS = 6 * 60 * 60 * 1000L
}
