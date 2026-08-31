package com.yinxing.launcher.feature.weather

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.Shadows.shadowOf
import com.yinxing.launcher.data.weather.WeatherPreferences

@RunWith(RobolectricTestRunner::class)
class WeatherCityManagerActivitySmokeTest {
    private var activityController: ActivityController<WeatherCityManagerActivity>? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        WeatherPreferences.resetForTest()
    }

    @After
    fun tearDown() {
        activityController?.destroy()
        activityController = null
        WeatherPreferences.resetForTest()
    }

    @Test
    fun `city manager keeps only a simple add city action at the bottom`() {
        val activity = buildActivity()

        assertEquals(
            "添加城市",
            activity.findViewById<android.widget.TextView>(R.id.tv_add_city).text.toString(),
        )
        assertEquals(
            "使用当前位置",
            activity.findViewById<android.widget.TextView>(R.id.tv_use_current_location).text.toString(),
        )
        assertEquals(
            "使用当前位置",
            activity.findViewById<android.view.View>(R.id.btn_use_current_location).contentDescription.toString(),
        )
        assertEquals(
            "添加城市",
            activity.findViewById<android.view.View>(R.id.btn_search_city).contentDescription.toString(),
        )
        assertEquals(1, activity.findViewById<android.widget.LinearLayout>(R.id.city_list_container).childCount)
    }

    @Test
    fun `city manager respects top and bottom safe areas`() {
        val activity = buildActivity()
        val root = activity.findViewById<android.view.View>(R.id.weather_city_root)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(72, root.paddingTop)
        assertEquals(96, root.paddingBottom)
    }

    @Test
    fun `current location uses the system permission flow without an app dialog`() {
        val activity = buildActivity()

        activity.findViewById<android.view.View>(R.id.btn_use_current_location).performClick()

        assertEquals(null, ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun `city manager uses the project light surface and dark text hierarchy`() {
        val activity = buildActivity()

        assertEquals(
            ContextCompat.getColor(activity, R.color.launcher_text_primary),
            activity.findViewById<android.widget.TextView>(R.id.tv_city_page_title).currentTextColor,
        )
        assertEquals(
            ContextCompat.getColor(activity, R.color.launcher_text_secondary),
            activity.findViewById<android.widget.TextView>(R.id.tv_city_page_hint).currentTextColor,
        )
    }

    @Test
    fun `city manager toolbar actions use bounded rounded feedback`() {
        val activity = buildActivity()

        listOf(R.id.btn_back, R.id.btn_manage).forEach { viewId ->
            val background = activity.findViewById<android.view.View>(viewId).background
            assertEquals(R.drawable.bg_weather_toolbar_action, shadowOf(background).createdFromResId)
        }
    }

    @Test
    fun `system back returns a changed city result`() {
        val activity = buildActivity()
        WeatherCityManagerActivity::class.java.getDeclaredField("selectionChanged").apply {
            isAccessible = true
            setBoolean(activity, true)
        }

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
    }

    private fun buildActivity(): WeatherCityManagerActivity =
        Robolectric.buildActivity(WeatherCityManagerActivity::class.java)
            .setup()
            .also { activityController = it }
            .get()
}
