package com.bajianfeng.launcher.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        LauncherAppRepository.getInstance(context).invalidateInstalledApps()
        LauncherAppRepository.getInstance(context).invalidateSelections()
    }

    @Test
    fun launchShowsBuiltInHomeItemsAndClock() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        val timeView = activity.findViewById<TextView>(R.id.tv_time)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if ((recyclerView.adapter?.itemCount ?: 0) >= 5) {
                return@repeat
            }
            Thread.sleep(50)
        }

        assertEquals(4, recyclerView.adapter?.itemCount)
        assertTrue(timeView.text.isNotBlank())
    }

    @Test
    fun clickingWeatherCardFallsBackToBrowserWhenNoVendorWeatherApp() {
        registerWeatherBrowser()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.layout_weather_entry).performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(activity.getString(R.string.weather_fallback_url), startedIntent.dataString)
    }

    @Suppress("DEPRECATION")
    private fun registerWeatherBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.weather_fallback_url)))
        val applicationInfo = ApplicationInfo().apply {
            packageName = "com.android.browser"
            nonLocalizedLabel = "Browser"
        }
        val activityInfo = ActivityInfo().apply {
            packageName = "com.android.browser"
            name = "com.android.browser.BrowserActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
