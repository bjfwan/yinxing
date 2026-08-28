package com.yinxing.launcher.data.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FallDetectionPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: LauncherPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        LauncherSettingsDataStore.getInstance(context).clear()
        preferences = LauncherPreferences.getInstance(context)
    }

    @Test
    fun fallDetectionIsOffUntilAValidFamilyNumberIsConfigured() {
        assertFalse(preferences.isFallDetectionEnabled())
        assertEquals("", preferences.getFallEmergencyContact())

        preferences.setFallDetectionEnabled(true)

        assertFalse(preferences.isFallDetectionEnabled())
    }

    @Test
    fun validFamilyNumberCanEnableFallDetection() {
        preferences.setFallEmergencyContact("138 1234-5678")
        preferences.setFallDetectionEnabled(true)

        assertEquals("13812345678", preferences.getFallEmergencyContact())
        assertTrue(preferences.isFallDetectionEnabled())
    }

    @Test
    fun clearingFamilyNumberAlsoDisablesDetection() {
        preferences.setFallEmergencyContact("13812345678")
        preferences.setFallDetectionEnabled(true)

        preferences.setFallEmergencyContact("")

        assertEquals("", preferences.getFallEmergencyContact())
        assertFalse(preferences.isFallDetectionEnabled())
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
