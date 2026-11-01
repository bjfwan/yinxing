package com.yinxing.launcher.common.lobster

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import org.json.JSONObject

data class LobsterDeviceState(
    val sdkInt: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val osRelease: String,
    val buildDisplay: String,
    val primaryAbi: String?,
    val webViewVersion: String?,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestScreenWidthDp: Int,
    val densityDpi: Int,
    val fontScale: Double,
    val orientation: String,
    val nightMode: Boolean,
    val navigationMode: String,
    val availableStorageMb: Long,
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val lowMemory: Boolean,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val accelerometerAvailable: Boolean,
    val callPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sdk_int", sdkInt)
        put("manufacturer", manufacturer.take(80))
        put("brand", brand.take(80))
        put("model", model.take(120))
        put("os_release", osRelease.take(40))
        put("build_display", buildDisplay.take(120))
        primaryAbi?.take(40)?.takeIf(String::isNotBlank)?.let { put("primary_abi", it) }
        webViewVersion?.take(80)?.takeIf(String::isNotBlank)?.let { put("webview_version", it) }
        put("screen_width_dp", screenWidthDp)
        put("screen_height_dp", screenHeightDp)
        put("smallest_screen_width_dp", smallestScreenWidthDp)
        put("density_dpi", densityDpi)
        put("font_scale", fontScale)
        put("orientation", orientation)
        put("night_mode", nightMode)
        put("navigation_mode", navigationMode)
        put("available_storage_mb", availableStorageMb)
        put("available_memory_mb", availableMemoryMb)
        put("total_memory_mb", totalMemoryMb)
        put("low_memory", lowMemory)
        batteryPercent?.let { put("battery_percent", it) }
        put("power_save_mode", powerSaveMode)
        put("battery_optimization_ignored", batteryOptimizationIgnored)
        put("accelerometer_available", accelerometerAvailable)
        put("call_permission_granted", callPermissionGranted)
        put("notification_permission_granted", notificationPermissionGranted)
    }

    fun toLogLine(): String {
        return buildString {
            append("[设备状态] Android=").append(sdkInt)
            append("; 设备=").append(manufacturer).append(' ').append(model)
            append("; 系统=").append(osRelease).append('/').append(buildDisplay)
            append("; ABI=").append(primaryAbi ?: "未知")
            append("; 屏幕dp=").append(screenWidthDp).append('x').append(screenHeightDp)
            append("; 密度dpi=").append(densityDpi)
            append("; 字体倍率=").append(fontScale)
            append("; 导航=").append(navigationMode)
            append("; 深色模式=").append(nightMode.yesOrNo())
            append("; 存储可用MB=").append(availableStorageMb)
            append("; 内存可用MB=").append(availableMemoryMb)
            append("; 内存总量MB=").append(totalMemoryMb)
            append("; 低内存=").append(lowMemory.yesOrNo())
            batteryPercent?.let { append("; 电量=").append(it).append('%') }
            append("; 省电模式=").append(powerSaveMode.yesOrNo())
            append("; 忽略电池优化=").append(batteryOptimizationIgnored.yesOrNo())
            append("; 加速度传感器=").append(accelerometerAvailable.yesOrNo())
            append("; 拨号权限=").append(callPermissionGranted.yesOrNo())
            append("; 通知权限=").append(notificationPermissionGranted.yesOrNo())
        }
    }

    private fun Boolean.yesOrNo(): String = if (this) "是" else "否"
}

internal object LobsterDeviceStateCollector {
    private const val BYTES_PER_MB = 1024L * 1024L

    fun capture(context: Context): LobsterDeviceState {
        val appContext = context.applicationContext
        val memoryInfo = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.getMemoryInfo(memoryInfo)
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) {
            batteryLevel * 100 / batteryScale
        } else {
            null
        }
        val powerSaveMode = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isPowerSaveMode == true
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val configuration = appContext.resources.configuration
        val displayMetrics = appContext.resources.displayMetrics
        val navigationMode = runCatching {
            Settings.Secure.getInt(appContext.contentResolver, "navigation_mode", -1)
        }.getOrDefault(-1).toNavigationMode()
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        return LobsterDeviceState(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            osRelease = Build.VERSION.RELEASE.orEmpty(),
            buildDisplay = Build.DISPLAY.orEmpty(),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull(),
            webViewVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
            } else {
                null
            },
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            densityDpi = displayMetrics.densityDpi,
            fontScale = configuration.fontScale.toDouble(),
            orientation = configuration.orientation.toWireOrientation(),
            nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            navigationMode = navigationMode,
            availableStorageMb = appContext.filesDir.usableSpace / BYTES_PER_MB,
            availableMemoryMb = memoryInfo.availMem / BYTES_PER_MB,
            totalMemoryMb = memoryInfo.totalMem / BYTES_PER_MB,
            lowMemory = memoryInfo.lowMemory,
            batteryPercent = batteryPercent,
            powerSaveMode = powerSaveMode,
            batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true,
            accelerometerAvailable = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            callPermissionGranted = appContext.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
            notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    private fun Int.toNavigationMode(): String = when (this) {
        0 -> "three_button"
        1 -> "two_button"
        2 -> "gesture"
        else -> "unknown"
    }

    private fun Int.toWireOrientation(): String = when (this) {
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_SQUARE -> "square"
        else -> "unknown"
    }
}
