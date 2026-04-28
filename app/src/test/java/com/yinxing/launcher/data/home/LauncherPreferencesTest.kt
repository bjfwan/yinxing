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
class LauncherPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: LauncherPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        HomeAppConfig(context).clear()
        LauncherSettingsDataStore.getInstance(context).clear()
        preferences = LauncherPreferences(context)
    }

    @Test
    fun setPackageSelectedUpdatesSelectionsAndSavedOrder() {
        preferences.setPackageSelected("pkg.alpha", true)
        preferences.setPackageSelected("pkg.beta", true)
        preferences.setPackageSelected("pkg.alpha", false)

        assertEquals(setOf("pkg.beta"), preferences.getSelectedPackages())
        assertEquals(listOf("pkg.beta"), preferences.getAppOrder())
    }

    @Test
    fun syncAppOrderDropsPackagesThatAreNoLongerSelected() {
        preferences.saveAppOrder(listOf("pkg.alpha", "pkg.beta", "pkg.gamma"))

        preferences.syncAppOrder(listOf("pkg.gamma", "pkg.alpha"))

        assertEquals(listOf("pkg.alpha", "pkg.gamma"), preferences.getAppOrder())
    }


    @Test
    fun autoAnswerDefaultIsTrue() {
        assertTrue(preferences.isAutoAnswerEnabled())
    }

    @Test
    fun autoAnswerCanBeDisabled() {
        preferences.setAutoAnswerEnabled(false)
        assertFalse(preferences.isAutoAnswerEnabled())
    }

    @Test
    fun autoAnswerPersistsAcrossInstances() {
        preferences.setAutoAnswerEnabled(false)
        assertFalse(LauncherPreferences(context).isAutoAnswerEnabled())
    }

    @Test
    fun autoAnswerCanBeReEnabled() {
        preferences.setAutoAnswerEnabled(false)
        preferences.setAutoAnswerEnabled(true)
        assertTrue(preferences.isAutoAnswerEnabled())
    }

    @Test
    fun autoStartConfirmationDefaultsToFalse() {
        assertFalse(preferences.isAutoStartConfirmed())
    }

    @Test
    fun backgroundStartConfirmationDefaultsToFalse() {
        assertFalse(preferences.isBackgroundStartConfirmed())
    }

    @Test
    fun manualGuardConfirmationsPersistAcrossInstances() {
        preferences.setAutoStartConfirmed(true)
        preferences.setBackgroundStartConfirmed(true)

        val restored = LauncherPreferences(context)

        assertTrue(restored.isAutoStartConfirmed())
        assertTrue(restored.isBackgroundStartConfirmed())
    }

    @Test
    fun reservedPreferenceKeysAreNotSelectedPackages() {
        preferences.setLowPerformanceModeEnabled(true)
        preferences.setAutoAnswerEnabled(false)
        preferences.setAutoAnswerDelaySeconds(12)
        preferences.setFullCardTapEnabled(true)
        preferences.setDarkMode(LauncherPreferences.DARK_MODE_DARK)
        preferences.setKioskModeEnabled(true)
        preferences.setAutoStartConfirmed(true)
        preferences.setBackgroundStartConfirmed(true)
        preferences.setIconScale(110)
        preferences.saveAppOrder(listOf("pkg.alpha"))
        preferences.setPackageSelected("pkg.beta", true)

        assertEquals(setOf("pkg.beta"), preferences.getSelectedPackages())
    }

    @Test
    fun legacyLauncherPrefsMigratesToSplitStoresAndClearsOldKeys() {
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("pkg.alpha", true)
            .putBoolean("pkg.beta", false)
            .putString("app_order", "pkg.beta,pkg.alpha")
            .putBoolean("low_performance_mode", true)
            .putBoolean("auto_answer_enabled", false)
            .putInt("auto_answer_delay_seconds", 9)
            .putBoolean("full_card_tap_enabled", true)
            .putString("dark_mode", LauncherPreferences.DARK_MODE_DARK)
            .putBoolean("kiosk_mode_enabled", true)
            .putBoolean("autostart_confirmed", true)
            .putBoolean("background_start_confirmed", true)
            .putInt("icon_scale", 115)
            .commit()

        val migrated = LauncherPreferences(context)
        val legacy = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

        assertEquals(setOf("pkg.alpha"), migrated.getSelectedPackages())
        assertEquals(listOf("pkg.alpha"), migrated.getAppOrder())
        assertTrue(migrated.isLowPerformanceModeEnabled())
        assertFalse(migrated.isAutoAnswerEnabled())
        assertEquals(9, migrated.getAutoAnswerDelaySeconds())
        assertTrue(migrated.isFullCardTapEnabled())
        assertEquals(LauncherPreferences.DARK_MODE_DARK, migrated.getDarkMode())
        assertTrue(migrated.isKioskModeEnabled())
        assertTrue(migrated.isAutoStartConfirmed())
        assertTrue(migrated.isBackgroundStartConfirmed())
        assertEquals(115, migrated.getIconScale())
        assertTrue(legacy.all.isEmpty())
    }
}
