package com.yinxing.launcher.feature.fall

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import org.junit.Assert.assertEquals
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
class FallAlertActivityTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        LauncherSettingsDataStore.getInstance(application).clear()
        LauncherPreferences.getInstance(application).setFallEmergencyContact("13812345678")
    }

    @Test
    fun alertStartsWithThirtySecondCountdownAndLargeActions() {
        val controller = Robolectric.buildActivity(FallAlertActivity::class.java).setup()
        val activity = controller.get()

        assertEquals("30", activity.findViewById<TextView>(R.id.fall_alert_countdown).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.fall_alert_cancel).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.fall_alert_call_now).visibility)

        controller.destroy()
    }

    @Test
    fun imOkayStopsTheAlertAndFinishesThePage() {
        val controller = Robolectric.buildActivity(FallAlertActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.fall_alert_cancel).performClick()

        assertTrue(activity.isFinishing)
        assertEquals(
            FallDetectionService.ACTION_FALSE_ALARM,
            shadowOf(application).nextStartedService.action
        )
        controller.destroy()
    }

    @Test
    @Config(sdk = [24])
    fun alertCanOpenOnAndroidSevenWithoutCallingNewerPlatformApis() {
        val controller = Robolectric.buildActivity(FallAlertActivity::class.java).setup()

        assertEquals(
            "30",
            controller.get().findViewById<TextView>(R.id.fall_alert_countdown).text.toString()
        )

        controller.destroy()
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
