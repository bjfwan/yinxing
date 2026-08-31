package com.yinxing.launcher.feature.incoming

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class IncomingCallForegroundServiceTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        IncomingCallDiagnostics.clear(application)
    }

    @Test
    fun ensureNotificationChannelsCreatesIncomingCallChannel() {
        IncomingCallForegroundService.ensureNotificationChannels(application)

        val manager = application.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(IncomingCallForegroundService.CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(
            application.getString(R.string.incoming_call_notification_channel_name),
            channel?.name?.toString()
        )
        assertEquals(
            application.getString(R.string.incoming_call_notification_channel_description),
            channel?.description
        )
    }

    @Test
    fun startCommandPromotesServiceToForegroundWithCallNotification() {
        val service = startService(callerName = "李阿姨", autoAnswer = true)
        val notification = foregroundNotificationOf(service)

        assertNotNull(notification)
        val visibleLabels = listOfNotNull(
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertTrue(visibleLabels.contains("李阿姨"))
        assertEquals(Notification.CATEGORY_CALL, notification.category)
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(2, notification.actions.size)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun fullScreenIntentLaunchesIncomingCallActivityWithoutTriggerAction() {
        val service = startService(callerName = "王叔叔", autoAnswer = true)
        val notification = foregroundNotificationOf(service)

        notification.fullScreenIntent.send()
        idleMainLooper()

        val startedIntent = shadowOf(application).nextStartedActivity
        assertEquals(IncomingCallActivity::class.java.name, startedIntent.component?.className)
        assertEquals("王叔叔", startedIntent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NAME))
        assertEquals(true, startedIntent.getBooleanExtra(IncomingCallActivity.EXTRA_AUTO_ANSWER, false))
        assertEquals(null, startedIntent.getStringExtra(IncomingCallActivity.EXTRA_TRIGGER_ACTION))
    }

    @Test
    fun ongoingCallStyleKeepsAFullScreenIntentForModernAndroidEligibility() {
        val notification = IncomingCallNotificationController.buildOngoing(
            application,
            callerName = "李阿姨"
        )

        assertNotNull(notification.fullScreenIntent)
    }

    @Test
    fun notificationActionsLaunchIncomingCallActivityWithAcceptAndDeclineTriggers() {
        val service = startService(callerName = "赵大爷", autoAnswer = false)
        val notification = foregroundNotificationOf(service)

        val triggers = notification.actions.map { action ->
            action.actionIntent.send()
            idleMainLooper()
            val startedIntent = shadowOf(application).nextStartedActivity
            assertEquals(IncomingCallActivity::class.java.name, startedIntent.component?.className)
            assertEquals("赵大爷", startedIntent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NAME))
            assertEquals(false, startedIntent.getBooleanExtra(IncomingCallActivity.EXTRA_AUTO_ANSWER, true))
            startedIntent.getStringExtra(IncomingCallActivity.EXTRA_TRIGGER_ACTION)
        }
        assertEquals(
            setOf(
                IncomingCallActivity.TRIGGER_ACTION_ACCEPT,
                IncomingCallActivity.TRIGGER_ACTION_DECLINE
            ),
            triggers.toSet()
        )
    }

    private fun startService(callerName: String?, autoAnswer: Boolean): IncomingCallForegroundService {
        val intent = Intent(application, IncomingCallForegroundService::class.java).apply {
            action = IncomingCallForegroundService.ACTION_SHOW_INCOMING_CALL
            putExtra(IncomingCallForegroundService.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallForegroundService.EXTRA_AUTO_ANSWER, autoAnswer)
        }
        val controller = Robolectric.buildService(IncomingCallForegroundService::class.java, intent).create()
        val service = controller.get()
        service.onStartCommand(intent, 0, 1)
        idleMainLooper()
        return service
    }

    private fun foregroundNotificationOf(service: IncomingCallForegroundService): Notification {
        return requireNotNull(shadowOf(service).lastForegroundNotification)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
