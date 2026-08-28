package com.yinxing.launcher.common.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFontScaleTest {
    @Test
    fun followSystemKeepsTheSystemFontScale() {
        assertEquals(1.27f, AppFontScale.resolve(AppFontScale.SYSTEM, 1.27f), 0.001f)
    }

    @Test
    fun appPresetsResolveToStableFontScales() {
        assertEquals(1.0f, AppFontScale.resolve(AppFontScale.STANDARD, 1.27f), 0.001f)
        assertEquals(1.15f, AppFontScale.resolve(AppFontScale.LARGE, 1.0f), 0.001f)
        assertEquals(1.30f, AppFontScale.resolve(AppFontScale.EXTRA_LARGE, 1.0f), 0.001f)
    }

    @Test
    fun wrappedContextUsesTheSelectedAppFontScale() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val wrapped = AppFontScale.wrap(context, AppFontScale.EXTRA_LARGE)

        assertEquals(1.30f, wrapped.resources.configuration.fontScale, 0.001f)
    }
}
