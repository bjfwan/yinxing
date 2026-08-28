package com.yinxing.launcher.feature.weather

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
class WeatherCityHintPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(WeatherCityHintPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstWeatherDetailVisitCanShowCityHintOnlyOnce() {
        val preferences = WeatherCityHintPreferences(context)

        assertTrue(preferences.markShownIfFirstTime())
        assertFalse(preferences.markShownIfFirstTime())
    }

    @Test
    fun shownCityHintStaysHiddenForLaterWeatherDetailVisits() {
        WeatherCityHintPreferences(context).markShownIfFirstTime()

        assertFalse(WeatherCityHintPreferences(context).markShownIfFirstTime())
    }
}
