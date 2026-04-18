package com.bajianfeng.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import android.widget.TextView

import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.feature.incoming.IncomingCallDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        IncomingCallDiagnostics.clear(context)
        registerSettingsActivity()
        registerHomeActivity(packageName = "com.android.launcher3")
    }


    @Test
    fun kioskModeSwitchDefaultsToOffAndShowsCorrectSummary() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_kiosk_mode)
        val summaryView = activity.findViewById<TextView>(R.id.tv_kiosk_mode_summary)

        assertFalse(switchView.isChecked)

        assertEquals(
            activity.getString(R.string.settings_kiosk_mode_summary_off),
            summaryView.text.toString()
        )
    }

    @Test
    fun kioskModeSwitchToggleOnRequiresDefaultLauncher() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_kiosk_mode)
        val summaryView = activity.findViewById<TextView>(R.id.tv_kiosk_mode_summary)

        switchView.performClick()
        idle()

        assertFalse(switchView.isChecked)

        assertEquals(
            activity.getString(R.string.settings_kiosk_mode_summary_off),
            summaryView.text.toString()
        )
        assertFalse(LauncherPreferences.getInstance(context).isKioskModeEnabled())
    }

    @Test
    fun kioskModeSwitchReflectsSavedEnabledState() {
        LauncherPreferences.getInstance(context).setKioskModeEnabled(true)

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_kiosk_mode)
        val summaryView = activity.findViewById<TextView>(R.id.tv_kiosk_mode_summary)

        assertTrue(switchView.isChecked)

        assertEquals(
            activity.getString(R.string.settings_kiosk_mode_summary_on),
            summaryView.text.toString()
        )
        assertTrue(LauncherPreferences.getInstance(context).isKioskModeEnabled())
    }

    @Test
    fun autoAnswerDefaultsToEnabledAndShowsDelaySummary() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_auto_answer)
        val summaryView = activity.findViewById<TextView>(R.id.tv_auto_answer_summary)
        val delayView = activity.findViewById<TextView>(R.id.tv_auto_answer_delay_summary)

        assertTrue(switchView.isChecked)
        assertEquals(
            activity.getString(R.string.settings_auto_answer_summary_on),
            summaryView.text.toString()
        )
        assertEquals(
            activity.getString(
                R.string.settings_auto_answer_delay_summary,
                LauncherPreferences.DEFAULT_AUTO_ANSWER_DELAY_SECONDS
            ),
            delayView.text.toString()
        )
    }

    @Test
    fun incomingTraceSummaryShowsLatestCallChain() {
        IncomingCallDiagnostics.recordBroadcastReceived(
            context = context,
            callerLabel = "张阿姨",
            incomingNumber = "13812345678",
            autoAnswer = true
        )
        IncomingCallDiagnostics.recordServiceStarted(context, "张阿姨", autoAnswer = true)
        IncomingCallDiagnostics.recordActivityShown(context, "张阿姨")
        IncomingCallDiagnostics.recordAcceptSuccess(
            context,
            context.getString(R.string.incoming_call_status_accept_sent)
        )

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle()
        val summaryView = activity.findViewById<TextView>(R.id.tv_incoming_trace_summary)

        assertTrue(summaryView.text.contains(activity.getString(R.string.incoming_call_trace_accept_success)))
        assertTrue(summaryView.text.contains("张阿姨"))
    }


    @Suppress("DEPRECATION")
    private fun registerSettingsActivity() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        val applicationInfo = ApplicationInfo().apply {
            packageName = "com.android.settings"
            nonLocalizedLabel = "Settings"
        }
        val activityInfo = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "com.android.settings.Settings"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    @Suppress("DEPRECATION")
    private fun registerHomeActivity(packageName: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = "OldLauncher"
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.feature.home.MainActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}


