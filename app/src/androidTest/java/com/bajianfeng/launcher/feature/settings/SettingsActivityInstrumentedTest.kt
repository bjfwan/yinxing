package com.bajianfeng.launcher.feature.settings

import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.testutil.InstrumentationTestEnvironment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityInstrumentedTest {
    @Before
    fun setUp() {
        InstrumentationTestEnvironment.resetAppState()
    }

    @Test
    fun toggleLowPerformanceModeUpdatesSummaryAndPreference() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val switch = activity.findViewById<SwitchCompat>(R.id.switch_low_performance)
                val summary = activity.findViewById<android.widget.TextView>(R.id.tv_low_performance_summary)
                val preferences = LauncherPreferences.getInstance(activity)

                org.junit.Assert.assertFalse(switch.isChecked)
                org.junit.Assert.assertEquals(
                    activity.getString(R.string.settings_low_performance_summary_off),
                    summary.text.toString()
                )

                switch.performClick()

                org.junit.Assert.assertTrue(switch.isChecked)
                org.junit.Assert.assertTrue(preferences.isLowPerformanceModeEnabled())
                org.junit.Assert.assertEquals(
                    activity.getString(R.string.settings_low_performance_summary_on),
                    summary.text.toString()
                )
            }
        }
    }

    @Test
    fun toggleAutoAnswerUpdatesSummaryAndPreference() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val switch = activity.findViewById<SwitchCompat>(R.id.switch_auto_answer)
                val summary = activity.findViewById<android.widget.TextView>(R.id.tv_auto_answer_summary)
                val preferences = LauncherPreferences.getInstance(activity)

                org.junit.Assert.assertTrue(switch.isChecked)
                org.junit.Assert.assertTrue(preferences.isAutoAnswerEnabled())

                switch.performClick()

                org.junit.Assert.assertFalse(switch.isChecked)
                org.junit.Assert.assertFalse(preferences.isAutoAnswerEnabled())
                org.junit.Assert.assertEquals(
                    activity.getString(R.string.settings_auto_answer_summary_off),
                    summary.text.toString()
                )
            }
        }
    }
}
