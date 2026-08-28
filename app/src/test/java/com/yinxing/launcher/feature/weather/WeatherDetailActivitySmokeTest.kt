package com.yinxing.launcher.feature.weather

import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WeatherDetailActivitySmokeTest {
    @Test
    fun `clicking city opens city weather manager`() {
        val activity = Robolectric.buildActivity(WeatherDetailActivity::class.java).setup().get()

        activity.findViewById<android.view.View>(R.id.tv_city).performClick()

        val intent = shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(WeatherCityManagerActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `weather content respects top and bottom system safe areas`() {
        val activity = Robolectric.buildActivity(WeatherDetailActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.ViewGroup>(R.id.weather_detail_scroll)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(72, root.paddingTop)
        assertEquals(96, root.paddingBottom)
        assertTrue(root.clipToPadding)
    }

    @Test
    fun `weather detail actions use bounded rounded feedback`() {
        val activity = Robolectric.buildActivity(WeatherDetailActivity::class.java).setup().get()

        listOf(R.id.btn_back, R.id.tv_city).forEach { viewId ->
            val background = activity.findViewById<android.view.View>(viewId).background
            assertEquals(R.drawable.bg_weather_toolbar_action, shadowOf(background).createdFromResId)
        }
    }
}
