package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterDeviceStateTest {
    @Test
    fun `serializes only non sensitive diagnostic state`() {
        val json = LobsterDeviceState(
            sdkInt = 35,
            availableStorageMb = 1024,
            availableMemoryMb = 2048,
            totalMemoryMb = 8192,
            lowMemory = false,
            batteryPercent = 76,
            powerSaveMode = true,
            callPermissionGranted = false,
            notificationPermissionGranted = true
        ).toJson()

        assertEquals(35, json.getInt("sdk_int"))
        assertEquals(1024L, json.getLong("available_storage_mb"))
        assertEquals(76, json.getInt("battery_percent"))
        assertFalse(json.toString().contains("device_id"))
        assertFalse(json.toString().contains("phone_number"))

        val logLine = LobsterDeviceState(
            sdkInt = 35,
            availableStorageMb = 1024,
            availableMemoryMb = 2048,
            totalMemoryMb = 8192,
            lowMemory = false,
            batteryPercent = 76,
            powerSaveMode = true,
            callPermissionGranted = false,
            notificationPermissionGranted = true
        ).toLogLine()
        assertEquals(
            "[设备状态] Android=35; 存储可用MB=1024; 内存可用MB=2048; 内存总量MB=8192; " +
                "低内存=否; 电量=76%; 省电模式=是; 拨号权限=否; 通知权限=是",
            logLine
        )
    }
}
