package com.yinxing.launcher.feature.settings

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yinxing.launcher.R
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
    fun autoAnswerEntryOpensCallsSettings() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_card_auto_answer)).perform(click())
            onView(withId(R.id.settings_detail_page_title)).check(
                matches(withText(appContext.getString(R.string.settings_calls_title)))
            )
        }
    }

    @Test
    fun canSwitchBetweenStandardAndElderSettings() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_switch_mode)).perform(click())
            onView(withId(R.id.settings_overlay_title)).check(
                matches(withText(appContext.getString(R.string.settings_elder_title)))
            )
            onView(withId(R.id.btn_switch_standard)).perform(click())
            onView(withId(R.id.settings_page_title)).check(
                matches(withText(appContext.getString(R.string.settings_title)))
            )
        }
    }

    @Test
    fun standardEntryOpensSecondaryPage() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_detail_contacts)).perform(click())
            onView(withId(R.id.settings_detail_page_title)).check(
                matches(withText(appContext.getString(R.string.settings_contacts_title)))
            )
        }
    }
}
