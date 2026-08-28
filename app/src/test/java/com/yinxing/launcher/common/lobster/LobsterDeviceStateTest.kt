package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterDeviceStateTest {
    @Test
    fun `serializes only non sensitive diagnostic state`() {
        val json = LobsterDeviceState(
            sdkInt = 35,
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            osRelease = "15",
            buildDisplay = "AP4A.260805.001",
            primaryAbi = "arm64-v8a",
            webViewVersion = "139.0.7258.94",
            screenWidthDp = 412,
            screenHeightDp = 915,
            smallestScreenWidthDp = 412,
            densityDpi = 420,
            fontScale = 1.15,
            orientation = "portrait",
            nightMode = true,
            navigationMode = "gesture",
            availableStorageMb = 1024,
            availableMemoryMb = 2048,
            totalMemoryMb = 8192,
            lowMemory = false,
            batteryPercent = 76,
            powerSaveMode = true,
            batteryOptimizationIgnored = false,
            accelerometerAvailable = true,
            callPermissionGranted = false,
            notificationPermissionGranted = true
        ).toJson()

        assertEquals(35, json.getInt("sdk_int"))
        assertEquals(1024L, json.getLong("available_storage_mb"))
        assertEquals(76, json.getInt("battery_percent"))
        assertEquals("Google", json.getString("manufacturer"))
        assertEquals("Pixel 9", json.getString("model"))
        assertEquals("139.0.7258.94", json.getString("webview_version"))
        assertEquals(412, json.getInt("screen_width_dp"))
        assertEquals(420, json.getInt("density_dpi"))
        assertEquals(1.15, json.getDouble("font_scale"), 0.001)
        assertEquals("gesture", json.getString("navigation_mode"))
        assertFalse(json.toString().contains("device_id"))
        assertFalse(json.toString().contains("phone_number"))

        val logLine = LobsterDeviceState(
            sdkInt = 35,
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            osRelease = "15",
            buildDisplay = "AP4A.260805.001",
            primaryAbi = "arm64-v8a",
            webViewVersion = "139.0.7258.94",
            screenWidthDp = 412,
            screenHeightDp = 915,
            smallestScreenWidthDp = 412,
            densityDpi = 420,
            fontScale = 1.15,
            orientation = "portrait",
            nightMode = true,
            navigationMode = "gesture",
            availableStorageMb = 1024,
            availableMemoryMb = 2048,
            totalMemoryMb = 8192,
            lowMemory = false,
            batteryPercent = 76,
            powerSaveMode = true,
            batteryOptimizationIgnored = false,
            accelerometerAvailable = true,
            callPermissionGranted = false,
            notificationPermissionGranted = true
        ).toLogLine()
        assertFalse(logLine.contains("device_id"))
        assertEquals(true, logLine.contains("Google Pixel 9"))
        assertEquals(true, logLine.contains("屏幕dp=412x915"))
        assertEquals(true, logLine.contains("导航=gesture"))
    }
}
