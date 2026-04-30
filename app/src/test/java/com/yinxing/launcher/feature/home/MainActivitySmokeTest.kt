package com.yinxing.launcher.feature.home

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
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
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
        val statusCard = activity.findViewById<View>(R.id.card_home_status)
        val timeView = activity.findViewById<TextView>(R.id.tv_time)
        waitUntil {
            recyclerView.adapter?.itemCount == 3 && statusCard.visibility == View.GONE
        }

        assertEquals(3, recyclerView.adapter?.itemCount)
        assertEquals(View.GONE, statusCard.visibility)
        assertTrue(timeView.text.isNotBlank())
    }

    @Test
    fun selectedAppsStillAppearAfterFixedPhoneAndWechatEntries() {
        registerLauncherApp(packageName = "pkg.camera", appLabel = "Camera")
        registerLauncherApp(packageName = "pkg.browser", appLabel = "Browser")

        val preferences = LauncherPreferences(context)
        preferences.setPackageSelected("pkg.camera", true)
        preferences.setPackageSelected("pkg.browser", true)
        preferences.saveAppOrder(listOf("pkg.browser", "pkg.camera"))

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        val statusCard = activity.findViewById<View>(R.id.card_home_status)
        waitUntil {
            recyclerView.adapter?.itemCount == 5 && statusCard.visibility == View.GONE
        }

        val adapter = recyclerView.adapter as HomeAppAdapter
        assertEquals(
            listOf("phone", "wechat_video", "pkg.browser", "pkg.camera", "add"),
            adapter.currentList.map { it.packageName }
        )
        assertEquals(View.GONE, statusCard.visibility)
    }

    @Test
    fun clickingWeatherCardFallsBackToBrowserWhenNoVendorWeatherApp() {
        registerWeatherBrowser()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.card_weather).performClick()

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

    @Suppress("DEPRECATION")
    private fun registerLauncherApp(packageName: String, appLabel: String) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = appLabel
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(launcherIntent, resolveInfo)
    }


    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) {
                return
            }
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
