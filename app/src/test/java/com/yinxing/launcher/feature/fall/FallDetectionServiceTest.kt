package com.yinxing.launcher.feature.fall

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FallDetectionServiceTest {
    private lateinit var application: Application
    private lateinit var sensor: Sensor
    private lateinit var shadowSensorManager: ShadowSensorManager

    @Before
    fun setUp() {
        FallCallTransitionContext.resetForTest()
        application = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        LauncherSettingsDataStore.getInstance(application).clear()
        LauncherPreferences.getInstance(application).apply {
            setFallEmergencyContact("13812345678")
            setFallDetectionEnabled(true)
        }
        val sensorManager = application.getSystemService(SensorManager::class.java)
        shadowSensorManager = shadowOf(sensorManager)
        sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSensorManager.addSensor(Sensor.TYPE_ACCELEROMETER, sensor)
    }

    @Test
    fun configuredServiceStartsInForegroundAndRegistersAccelerometer() {
        val service = startService()
        val foreground = shadowOf(service).lastForegroundNotification

        assertNotNull(foreground)
        assertEquals(
            application.getString(R.string.fall_detection_notification_title),
            foreground.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertTrue(shadowSensorManager.hasListener(service, sensor))

        service.onDestroy()
    }

    @Test
    fun qualifyingMotionSequencePostsUrgentFallAlert() {
        val service = startService()

        for (timeMs in 0L..2_000L step 20L) sendVector(timeMs, 0f, 1f, 0f)
        for (timeMs in 2_020L..2_120L step 20L) sendVector(timeMs, 3.5f, 0f, 0f)
        for (timeMs in 2_140L..4_300L step 20L) sendVector(timeMs, 1f, 0f, 0f)

        val manager = application.getSystemService(NotificationManager::class.java)
        val alert = shadowOf(manager).allNotifications
            .firstOrNull { it.category == Notification.CATEGORY_ALARM }
        assertNotNull(alert)
        assertEquals(Notification.CATEGORY_ALARM, alert?.category)

        service.onDestroy()
    }

    @Test
    fun callTransitionSuppressesQualifyingMotionSequence() {
        val service = startService()
        FallCallTransitionContext.markTransition()

        for (timeMs in 0L..2_000L step 20L) sendVector(timeMs, 0f, 1f, 0f)
        for (timeMs in 2_020L..2_120L step 20L) sendVector(timeMs, 5.5f, 0f, 0f)
        for (timeMs in 2_140L..4_300L step 20L) sendVector(timeMs, 1f, 0f, 0f)

        val manager = application.getSystemService(NotificationManager::class.java)
        val alert = shadowOf(manager).allNotifications
            .firstOrNull { it.category == Notification.CATEGORY_ALARM }
        assertNull(alert)

        service.onDestroy()
    }

    private fun startService(): FallDetectionService {
        val intent = Intent(application, FallDetectionService::class.java)
            .setAction(FallDetectionService.ACTION_START)
        val controller = Robolectric.buildService(FallDetectionService::class.java, intent).create()
        return controller.get().also { it.onStartCommand(intent, 0, 1) }
    }

    private fun sendVector(timestampMs: Long, xG: Float, yG: Float, zG: Float) {
        val event = ShadowSensorManager.createSensorEvent(3)
        event.sensor = sensor
        event.timestamp = timestampMs * 1_000_000L
        event.values[0] = xG * 9.80665f
        event.values[1] = yG * 9.80665f
        event.values[2] = zG * 9.80665f
        shadowSensorManager.sendSensorEventToListeners(event, sensor)
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
