package com.yinxing.launcher.feature.settings

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.testutil.InstrumentationTestEnvironment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityInstrumentedTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        InstrumentationTestEnvironment.resetAppState()
    }

    @Test
    fun toggleLowPerformanceModeUpdatesSummaryAndPreference() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            val preferences = LauncherPreferences.getInstance(appContext)

            onView(withId(R.id.btn_card_device)).perform(scrollTo(), click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.switch_low_performance_sheet)).check(matches(isNotChecked()))
            onView(withId(R.id.tv_low_performance_sheet_summary)).check(
                matches(withText(appContext.getString(R.string.settings_low_performance_summary_off)))
            )
            org.junit.Assert.assertFalse(preferences.isLowPerformanceModeEnabled())

            onView(withId(R.id.switch_low_performance_sheet)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.switch_low_performance_sheet)).check(matches(isChecked()))
            onView(withId(R.id.tv_low_performance_sheet_summary)).check(
                matches(withText(appContext.getString(R.string.settings_low_performance_summary_on)))
            )
            org.junit.Assert.assertTrue(preferences.isLowPerformanceModeEnabled())
        }
    }

    @Test
    fun toggleAutoAnswerUpdatesSummaryAndPreference() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            val preferences = LauncherPreferences.getInstance(appContext)

            onView(withId(R.id.btn_card_auto_answer)).perform(scrollTo(), click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.switch_auto_answer_sheet)).check(matches(isChecked()))
            onView(withId(R.id.tv_auto_answer_sheet_summary)).check(
                matches(withText(appContext.getString(R.string.settings_auto_answer_summary_on)))
            )
            org.junit.Assert.assertTrue(preferences.isAutoAnswerEnabled())

            onView(withId(R.id.switch_auto_answer_sheet)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.switch_auto_answer_sheet)).check(matches(isNotChecked()))
            onView(withId(R.id.tv_auto_answer_sheet_summary)).check(
                matches(withText(appContext.getString(R.string.settings_auto_answer_summary_off)))
            )
            org.junit.Assert.assertFalse(preferences.isAutoAnswerEnabled())
        }
    }
}
