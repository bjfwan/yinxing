package com.yinxing.launcher.common.lobster

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject

data class LobsterDeviceState(
    val sdkInt: Int,
    val availableStorageMb: Long,
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val lowMemory: Boolean,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean,
    val callPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sdk_int", sdkInt)
        put("available_storage_mb", availableStorageMb)
        put("available_memory_mb", availableMemoryMb)
        put("total_memory_mb", totalMemoryMb)
        put("low_memory", lowMemory)
        batteryPercent?.let { put("battery_percent", it) }
        put("power_save_mode", powerSaveMode)
        put("call_permission_granted", callPermissionGranted)
        put("notification_permission_granted", notificationPermissionGranted)
    }

    fun toLogLine(): String {
        return buildString {
            append("[设备状态] Android=").append(sdkInt)
            append("; 存储可用MB=").append(availableStorageMb)
            append("; 内存可用MB=").append(availableMemoryMb)
            append("; 内存总量MB=").append(totalMemoryMb)
            append("; 低内存=").append(lowMemory.yesOrNo())
            batteryPercent?.let { append("; 电量=").append(it).append('%') }
            append("; 省电模式=").append(powerSaveMode.yesOrNo())
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

        return LobsterDeviceState(
            sdkInt = Build.VERSION.SDK_INT,
            availableStorageMb = appContext.filesDir.usableSpace / BYTES_PER_MB,
            availableMemoryMb = memoryInfo.availMem / BYTES_PER_MB,
            totalMemoryMb = memoryInfo.totalMem / BYTES_PER_MB,
            lowMemory = memoryInfo.lowMemory,
            batteryPercent = batteryPercent,
            powerSaveMode = powerSaveMode,
            callPermissionGranted = appContext.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
            notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
}
