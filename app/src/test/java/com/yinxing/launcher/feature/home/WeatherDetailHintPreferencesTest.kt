package com.yinxing.launcher.feature.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDetailHintPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(WeatherDetailHintPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun freshInstallCanShowWeatherDetailHintOnce() {
        val preferences = WeatherDetailHintPreferences(context)

        assertTrue(preferences.markShownIfFirstTime())
        assertFalse(preferences.markShownIfFirstTime())
    }

    @Test
    fun shownHintNeverAppearsAgainInANewPreferencesInstance() {
        WeatherDetailHintPreferences(context).markShownIfFirstTime()

        assertFalse(WeatherDetailHintPreferences(context).markShownIfFirstTime())
    }
}
