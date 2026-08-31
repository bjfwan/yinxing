package com.yinxing.launcher.feature.phone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PhoneContactLayoutPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("phone_contact_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToLargeAndPersistsGrid() {
        val preferences = PhoneContactLayoutPreferences(context)
        assertEquals(PhoneContactLayoutStyle.LARGE, preferences.get())
        preferences.set(PhoneContactLayoutStyle.GRID)
        assertEquals(PhoneContactLayoutStyle.GRID, PhoneContactLayoutPreferences(context).get())
    }
}
