package com.yinxing.launcher.data.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}
