package com.yinxing.launcher.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import com.yinxing.launcher.data.weather.WeatherNow
import com.yinxing.launcher.data.weather.WeatherForecastDay
import com.yinxing.launcher.data.weather.WeatherState
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.databinding.ActivityMainBinding
import com.yinxing.launcher.feature.weather.WeatherDetailActivity
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import org.junit.Assert.assertEquals

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        resetLauncherSettingsDataStoreSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("home_app_config", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        WeatherPreferences.resetForTest()
        WeatherPreferences.getInstance(context).markInitialLocationPermissionRequested()
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
            recyclerView.adapter?.itemCount == 2 && statusCard.visibility == View.GONE
        }

        assertEquals(2, recyclerView.adapter?.itemCount)
        val adapter = recyclerView.adapter as HomeAppAdapter
        assertEquals(R.drawable.ic_home_phone_designed, adapter.currentList[0].iconResId)
        assertEquals(R.drawable.ic_home_video_designed, adapter.currentList[1].iconResId)
        assertEquals(View.GONE, statusCard.visibility)
        assertTrue(timeView.text.isNotBlank())
        val adjustHome = activity.findViewById<View>(R.id.btn_adjust_home)
        assertEquals(View.VISIBLE, adjustHome.visibility)
        assertTrue(adjustHome.layoutParams.height >= (56 * activity.resources.displayMetrics.density).toInt())
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
            recyclerView.adapter?.itemCount == 4 && statusCard.visibility == View.GONE
        }

        val adapter = recyclerView.adapter as HomeAppAdapter
        assertEquals(
            listOf("phone", "wechat_video", "pkg.browser", "pkg.camera"),
            adapter.currentList.map { it.packageName }
        )
        assertEquals(View.GONE, statusCard.visibility)
    }

    @Test
    fun primaryActionCardsGrowWithoutInflatingTheirIcons() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        waitUntil { recyclerView.adapter?.itemCount == 2 }
        val adapter = recyclerView.adapter as HomeAppAdapter
        val holder = adapter.onCreateViewHolder(recyclerView, HomeAppAdapter.VIEW_TYPE_APP) as HomeAppAdapter.AppViewHolder

        adapter.onBindViewHolder(holder, 0)

        val density = activity.resources.displayMetrics.density
        val scaledDensity = activity.resources.displayMetrics.scaledDensity
        assertEquals((260 * density).toInt(), holder.card.layoutParams.height)
        assertEquals((116 * density).toInt(), holder.icon.layoutParams.width)
        assertEquals(28f, holder.name.textSize / scaledDensity, 0.1f)
    }

    @Test
    fun uncachedThirdPartyAppDoesNotFlashAndroidDefaultIcon() {
        registerLauncherApp(packageName = "pkg.camera", appLabel = "Camera")
        LauncherPreferences(context).setPackageSelected("pkg.camera", true)
        LauncherAppRepository.getInstance(context).invalidateSelections()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        waitUntil { recyclerView.adapter?.itemCount == 3 }
        val adapter = recyclerView.adapter as HomeAppAdapter
        val holder = adapter.onCreateViewHolder(recyclerView, HomeAppAdapter.VIEW_TYPE_APP) as HomeAppAdapter.AppViewHolder

        adapter.onBindViewHolder(holder, 2)

        assertNull(holder.icon.drawable)
        assertEquals(0f, holder.icon.alpha, 0f)
    }

    @Test
    fun clickingWeatherCardOpensInAppWeatherDetails() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.card_weather).performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(WeatherDetailActivity::class.java.name, startedIntent.component?.className)
    }

    @Test
    fun clickingAdjustHomeOpensAppManager() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.btn_adjust_home).performClick()

        assertEquals(
            AppManageActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className
        )
    }

    @Test
    fun pendingDefaultLauncherFlowReturnsToDeviceSettingsAndConsumesFlag() {
        context.getSharedPreferences("settings_return", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("return_to_device_settings", true)
            .commit()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        val startedIntent = shadowOf(activity).nextStartedActivity

        assertNotNull(startedIntent)
        assertEquals(SettingsActivity::class.java.name, startedIntent.component?.className)
        assertEquals("device", startedIntent.getStringExtra(SettingsActivity.EXTRA_SECTION))
        assertTrue(
            !context.getSharedPreferences("settings_return", Context.MODE_PRIVATE)
                .getBoolean("return_to_device_settings", false)
        )
    }

    @Test
    fun calendarSummaryUsesSeparateSingleLineRows() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val dateView = activity.findViewById<TextView>(R.id.tv_date)
        val lunarView = activity.findViewById<TextView>(R.id.tv_lunar)

        assertEquals(1, dateView.maxLines)
        assertEquals(1, lunarView.maxLines)
        assertTrue(lunarView.parent === dateView.parent)
        assertEquals(
            ContextCompat.getColor(activity, R.color.launcher_text_secondary),
            lunarView.currentTextColor
        )
    }

    @Test
    fun weatherTemperatureOnlyUsesSpaceWhenWeatherIsAvailable() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        val controller = WeatherHeaderController(binding)

        controller.renderWeather(WeatherState.Loading("杭州"))
        assertEquals(View.GONE, binding.cardWeather.tvWeatherTemp.visibility)
        assertEquals(View.GONE, binding.cardWeather.ivWeatherIcon.visibility)

        controller.renderWeather(
            WeatherState.Success(
                cityName = "杭州",
                adcode = "330100",
                now = WeatherNow("杭州", "晴", 28, "东风", "2级", 60, "09:30"),
                forecast = listOf(WeatherForecastDay("2026-08-27", "小雨", "小雨", 30, 22, "0")),
                lastFetchTime = 1L
            )
        )
        assertEquals(View.VISIBLE, binding.cardWeather.tvWeatherTemp.visibility)
        assertEquals(View.VISIBLE, binding.cardWeather.ivWeatherIcon.visibility)
        assertEquals("晴", binding.cardWeather.tvWeatherDesc.text.toString())
        assertEquals("今日小雨 · 30°/22° · 09:30更新", binding.cardWeather.tvWeatherUpdate.text.toString())
    }

    @Test
    fun homeShowsAnIconForRainyWeather() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))

        WeatherHeaderController(binding).renderWeather(
            WeatherState.Success(
                cityName = "杭州",
                adcode = "330100",
                now = WeatherNow("杭州", "小雨", 24, "东风", "2级", 80, "09:30"),
                forecast = emptyList(),
                lastFetchTime = 1L,
            )
        )

        assertEquals(View.VISIBLE, binding.cardWeather.ivWeatherIcon.visibility)
        assertEquals(R.drawable.weather_rain, shadowOf(binding.cardWeather.ivWeatherIcon.drawable).createdFromResId)
    }

    @Test
    fun primaryHomeInformationUsesElderFriendlyTypeScale() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        WeatherHeaderController(binding).applyScale(100)
        val scaledDensity = activity.resources.displayMetrics.scaledDensity

        assertEquals(56f, binding.tvTime.textSize / scaledDensity, 0.1f)
        assertEquals(20f, binding.tvDate.textSize / scaledDensity, 0.1f)
        assertEquals(17f, binding.tvLunar.textSize / scaledDensity, 0.1f)
        assertEquals(46f, binding.cardWeather.tvWeatherTemp.textSize / scaledDensity, 0.1f)
        assertEquals(24f, binding.cardWeather.tvWeatherDesc.textSize / scaledDensity, 0.1f)
        assertEquals(18f, binding.cardWeather.tvWeatherUpdate.textSize / scaledDensity, 0.1f)
    }

    @Test
    fun weatherIconStaysCompact() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        val expectedSize = (36 * activity.resources.displayMetrics.density).toInt()

        assertEquals(expectedSize, binding.cardWeather.ivWeatherIcon.layoutParams.width)
        assertEquals(expectedSize, binding.cardWeather.ivWeatherIcon.layoutParams.height)
    }

    @Test
    fun homeWeatherCardKeepsOnlySummaryRows() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))

        assertEquals(2, (binding.cardWeather.root as ViewGroup).childCount)
    }

    @Test
    fun weatherIconAppearsBetweenCityAndUpdateTime() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        val header = binding.cardWeather.tvWeatherCity.parent as ViewGroup

        assertEquals(header, binding.cardWeather.ivWeatherIcon.parent)
        assertEquals(header, binding.cardWeather.tvWeatherTemp.parent)
        assertEquals(header, binding.cardWeather.tvWeatherDesc.parent)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, binding.cardWeather.tvWeatherCity.layoutParams.width)
        assertTrue(header.indexOfChild(binding.cardWeather.ivWeatherIcon) < header.indexOfChild(binding.cardWeather.tvWeatherCity))
        assertTrue(binding.cardWeather.tvWeatherUpdate.parent !== header)
    }

    @Test
    fun homeAddsTopSystemInsetToExistingSpacing() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        val originalTop = binding.root.paddingTop
        val statusBarHeight = 32
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarHeight, 0, 0))
            .build()

        ViewCompat.dispatchApplyWindowInsets(binding.root, insets)

        assertEquals(originalTop + statusBarHeight, binding.root.paddingTop)
    }

    @Test
    fun homeAddsBottomNavigationInsetToKeepAdjustButtonClear() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = ActivityMainBinding.bind(activity.findViewById(R.id.layout_home_root))
        val originalBottom = binding.root.paddingBottom
        val navigationBarHeight = 48
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBarHeight))
            .build()

        ViewCompat.dispatchApplyWindowInsets(binding.root, insets)

        assertEquals(originalBottom + navigationBarHeight, binding.root.paddingBottom)
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

    private fun resetLauncherSettingsDataStoreSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.settings.LauncherSettingsDataStore").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
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
