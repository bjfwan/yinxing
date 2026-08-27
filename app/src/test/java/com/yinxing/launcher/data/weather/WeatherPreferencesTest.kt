package com.yinxing.launcher.data.weather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: WeatherPreferences

    @Before
    fun setUp() {
        WeatherPreferences.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = WeatherPreferences.getInstance(context)
    }

    @Test
    fun `default city is also the first saved city`() {
        assertEquals("北京", preferences.getCityName())
        assertEquals(listOf("北京"), preferences.getSavedCities())
    }

    @Test
    fun `initial location permission request is remembered`() {
        assertEquals(false, preferences.wasInitialLocationPermissionRequested())

        preferences.markInitialLocationPermissionRequested()

        assertEquals(true, preferences.wasInitialLocationPermissionRequested())
    }

    @Test
    fun `adding cities preserves order and ignores duplicates`() {
        preferences.addCity(" 上海 ")
        preferences.addCity("广州")
        preferences.addCity("上海")

        assertEquals(listOf("北京", "上海", "广州"), preferences.getSavedCities())
    }

    @Test
    fun `selecting a city moves it to the front without losing saved cities`() {
        preferences.addCity("上海")
        preferences.addCity("广州")

        preferences.setCityName("广州")

        assertEquals("广州", preferences.getCityName())
        assertEquals(listOf("广州", "北京", "上海"), preferences.getSavedCities())
    }

    @Test
    fun `removing the active city selects the next saved city`() {
        preferences.addCity("上海")
        preferences.setCityName("上海")

        preferences.removeCity("上海")

        assertEquals("北京", preferences.getCityName())
        assertEquals(listOf("北京"), preferences.getSavedCities())
    }

    @Test
    fun `device location becomes current and remains available when returning to that city`() {
        preferences.setCurrentLocation("北京市", 39.9042, 116.4074)

        assertEquals("北京市", preferences.getCityName())
        assertEquals(
            SavedWeatherLocation("北京市", 39.9042, 116.4074),
            preferences.getSelectedLocation(),
        )

        preferences.setCityName("上海")
        assertEquals(null, preferences.getSelectedLocation())

        preferences.setCityName("北京市")
        assertEquals(
            SavedWeatherLocation("北京市", 39.9042, 116.4074),
            preferences.getSelectedLocation(),
        )
    }
}
