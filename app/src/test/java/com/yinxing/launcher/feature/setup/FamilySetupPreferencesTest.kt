package com.yinxing.launcher.feature.setup

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
class FamilySetupPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FamilySetupPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun freshInstallNeedsFamilySetup() {
        assertFalse(FamilySetupPreferences(context).isCompleted())
    }

    @Test
    fun completedSetupStaysCompleted() {
        val preferences = FamilySetupPreferences(context)

        preferences.markCompleted()

        assertTrue(FamilySetupPreferences(context).isCompleted())
    }

    @Test
    fun freshInstallNeedsAutomaticSetup() {
        assertTrue(
            FamilySetupPreferences(context).shouldLaunchAutomatically(
                firstInstallTime = 100L,
                lastUpdateTime = 100L,
            )
        )
    }

    @Test
    fun existingUserIsNotForcedIntoSetupAfterUpdating() {
        assertFalse(
            FamilySetupPreferences(context).shouldLaunchAutomatically(
                firstInstallTime = 100L,
                lastUpdateTime = 200L,
            )
        )
    }

    @Test
    fun freshInstallDecisionRemainsUntilSetupIsCompleted() {
        val preferences = FamilySetupPreferences(context)
        assertTrue(preferences.shouldLaunchAutomatically(100L, 100L))

        assertTrue(preferences.shouldLaunchAutomatically(100L, 200L))
    }
}
