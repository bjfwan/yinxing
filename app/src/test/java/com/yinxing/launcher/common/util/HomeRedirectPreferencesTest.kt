package com.yinxing.launcher.common.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeRedirectPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(HomeRedirectPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsToDisabledAndPersistsExplicitChoice() {
        val preferences = HomeRedirectPreferences(context)

        assertFalse(preferences.isEnabled())
        preferences.setEnabled(true)
        assertTrue(HomeRedirectPreferences(context).isEnabled())
    }
}
