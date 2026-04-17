package com.bajianfeng.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
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
        registerSettingsActivity()
    }

    @Test
    fun autoAnswerSwitchDefaultsToOnAndShowsCorrectSummary() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_auto_answer)
        val summaryView = activity.findViewById<TextView>(R.id.tv_auto_answer_summary)

        assertTrue(switchView.isChecked)
        assertEquals(
            activity.getString(R.string.settings_auto_answer_summary_on),
            summaryView.text.toString()
        )
    }

    @Test
    fun autoAnswerSwitchToggleOffUpdatesSummaryAndPreference() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_auto_answer)
        val summaryView = activity.findViewById<TextView>(R.id.tv_auto_answer_summary)

        switchView.performClick()   // ON → OFF

        assertFalse(switchView.isChecked)
        assertEquals(
            activity.getString(R.string.settings_auto_answer_summary_off),
            summaryView.text.toString()
        )
        assertFalse(LauncherPreferences.getInstance(context).isAutoAnswerEnabled())
    }

    @Test
    fun autoAnswerSwitchToggleOnOffOnRestoresState() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val switchView = activity.findViewById<SwitchCompat>(R.id.switch_auto_answer)

        switchView.performClick()   // ON → OFF
        switchView.performClick()   // OFF → ON

        assertTrue(switchView.isChecked)
        assertTrue(LauncherPreferences.getInstance(context).isAutoAnswerEnabled())
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

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
