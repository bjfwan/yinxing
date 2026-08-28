package com.yinxing.launcher.feature.fall

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FallFeatureManifestTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestRegistersPrivateHealthForegroundService() {
        val info = application.packageManager.getServiceInfo(
            ComponentName(application, FallDetectionService::class.java),
            PackageManager.ComponentInfoFlags.of(0)
        )

        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, info.foregroundServiceType)
    }

    @Test
    fun manifestRegistersPrivateLockScreenAlertActivity() {
        @Suppress("DEPRECATION")
        val info = application.packageManager.getActivityInfo(
            ComponentName(application, FallAlertActivity::class.java),
            0
        )

        assertFalse(info.exported)
    }

    @Test
    fun manifestDeclaresRequiredForegroundServicePermissions() {
        val packageInfo = application.packageManager.getPackageInfo(
            application.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_HEALTH in permissions)
        assertTrue(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS in permissions)
    }
}
