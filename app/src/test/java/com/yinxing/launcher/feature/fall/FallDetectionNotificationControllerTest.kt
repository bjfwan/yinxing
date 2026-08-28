package com.yinxing.launcher.feature.fall

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FallDetectionNotificationControllerTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun createsSeparateMonitoringAndUrgentAlertChannels() {
        FallDetectionNotificationController.ensureChannels(application)
        val manager = application.getSystemService(NotificationManager::class.java)

        assertEquals(
            application.getString(R.string.fall_detection_channel_name),
            manager.getNotificationChannel(FallDetectionNotificationController.MONITORING_CHANNEL_ID)
                .name.toString()
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(FallDetectionNotificationController.ALERT_CHANNEL_ID)
                .importance
        )
    }

    @Test
    fun urgentAlertCanOpenFullScreenAndOffersBothSafetyActions() {
        val notification = FallDetectionNotificationController.buildAlert(application)

        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(2, notification.actions.size)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
