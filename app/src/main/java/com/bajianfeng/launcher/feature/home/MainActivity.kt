package com.bajianfeng.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.data.weather.WeatherPreferences
import com.bajianfeng.launcher.data.weather.WeatherRepository
import com.bajianfeng.launcher.data.weather.WeatherState
import com.bajianfeng.launcher.feature.appmanage.AppManageActivity
import com.bajianfeng.launcher.feature.phone.PhoneContactActivity
import com.bajianfeng.launcher.feature.settings.SettingsActivity

import com.bajianfeng.launcher.feature.videocall.VideoCallActivity


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HomeAppAdapter
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvWeatherCity: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvLunar: TextView
    private lateinit var tvWeatherHighLow: TextView
    private lateinit var tvWeatherUpdate: TextView
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var weatherPreferences: WeatherPreferences
    private lateinit var itemMoveCallback: ItemMoveCallback
    private val appRepository by lazy { LauncherAppRepository.getInstance(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
    private var packageReceiverRegistered = false

    // 天气刷新间隔：3小时（腾讯每天6000次，心知充足）
    private val weatherRefreshInterval = 8 * 60 * 60 * 1000L

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            scheduleNextTimeUpdate()
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appRepository.invalidateInstalledApps()
            refreshApps()
        }
    }

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                launcherPreferences.isLowPerformanceModeKey(key) -> applyPerformanceMode()
                launcherPreferences.isHomeAppConfigKey(key) -> {
                    appRepository.invalidateSelections()
                    refreshApps()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launcherPreferences = LauncherPreferences.getInstance(this)
        weatherPreferences = WeatherPreferences.getInstance(this)
        launcherPreferences.registerListener(preferenceListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.home_toast_already_here),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // 顶部卡片 View
        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)
        tvLunar = findViewById(R.id.tv_lunar)
        tvWeatherCity = findViewById(R.id.tv_weather_city)
        tvWeatherDesc = findViewById(R.id.tv_weather_desc)
        tvWeatherTemp = findViewById(R.id.tv_weather_temp)
        tvWeatherHighLow = findViewById(R.id.tv_weather_high_low)
        tvWeatherUpdate = findViewById(R.id.tv_weather_update)
        findViewById<android.view.View>(R.id.layout_weather_entry).setOnClickListener {
            openWeatherEntry()
        }
        findViewById<android.view.View>(R.id.btn_family_settings).setOnClickListener {
            showCaregiverEntryDialog()
        }

        recyclerView = findViewById(R.id.recycler_home)


        val gridLayout = GridLayoutManager(this, 2)
        recyclerView.layoutManager = gridLayout
        recyclerView.setHasFixedSize(false)

        adapter = HomeAppAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onItemClick = { item -> handleAppClick(item) },
            onItemLongClick = { item -> handleAppLongClick(item) },
            onOrderChanged = { items -> saveAppOrder(items) }
        )
        recyclerView.adapter = adapter

        itemMoveCallback =
            ItemMoveCallback(adapter, !launcherPreferences.isLowPerformanceModeEnabled())
        val touchHelper = ItemTouchHelper(itemMoveCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.setTouchHelper(touchHelper)

        registerPackageReceiver()
        applyPerformanceMode()
        refreshApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateTime()
        scheduleNextTimeUpdate()
        // 仅缓存过期（>3小时）或城市变更时才重新请求
        maybeRefreshWeather()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTimeRunnable)
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver)
        }
        launcherPreferences.unregisterListener(preferenceListener)
        scope.cancel()
        super.onDestroy()
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
        packageReceiverRegistered = true
    }

    private fun applyPerformanceMode() {
        val lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled()
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 4 else 10)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
        adapter.setLowPerformanceMode(lowPerformanceMode)
        itemMoveCallback.setAnimateDrag(!lowPerformanceMode)
        updateTime()
        scheduleNextTimeUpdate()
    }

    private fun scheduleNextTimeUpdate() {
        handler.removeCallbacks(updateTimeRunnable)
        val interval =
            if (launcherPreferences.isLowPerformanceModeEnabled()) 60_000L else 1_000L
        val now = System.currentTimeMillis()
        val delay = interval - (now % interval)
        handler.postDelayed(updateTimeRunnable, if (delay == 0L) interval else delay)
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        tvTime.text = timeFormat.format(now.time)
        tvDate.text = dateFormat.format(now.time)
        tvLunar.text = getLunarDateString(now)
    }

    private fun getLunarDateString(cal: Calendar): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val chinese = android.icu.util.ChineseCalendar()
                chinese.timeInMillis = cal.timeInMillis
                val lunarMonth = chinese.get(android.icu.util.ChineseCalendar.MONTH) + 1
                val lunarDay = chinese.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
                val isLeap = chinese.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1
                val monthNames = arrayOf("", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
                val dayNames = arrayOf(
                    "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
                )
                val monthStr = (if (isLeap) "闰" else "") +
                    (monthNames.getOrNull(lunarMonth) ?: "$lunarMonth") + "月"
                val dayStr = dayNames.getOrNull(lunarDay) ?: "$lunarDay"
                "农历 $monthStr$dayStr"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun refreshApps() {
        scope.launch {
            adapter.submitList(appRepository.getHomeItems(launcherPreferences))
        }
    }

    private fun maybeRefreshWeather() {
        val city = weatherPreferences.getCityName()
        val cached = WeatherRepository.getCached()
        val expired = cached == null
            || cached.cityName != city
            || System.currentTimeMillis() - cached.lastFetchTime > weatherRefreshInterval
        if (expired) {
            scope.launch {
                val state = WeatherRepository.fetchWeather(city)
                applyWeatherToHeader(state)
            }
        } else {
            // 直接用缓存刷新 UI
            applyWeatherToHeader(cached!!)
        }
    }

    private fun applyWeatherToHeader(state: WeatherState) {
        val now = state.now
        val today = state.forecast.firstOrNull()
        if (now != null) {
            tvWeatherCity.text = now.cityName
            // 实况天气 + 今日白天预报（若不同则括号注明）
            val weatherText = if (today != null && today.textDay.isNotEmpty() && today.textDay != now.weather) {
                "${now.weather}（今日${today.textDay}）"
            } else {
                now.weather
            }
            tvWeatherDesc.text = "$weatherText  ${now.windDirection} ${now.windPower}"
            tvWeatherTemp.text = "${now.temperature}°"
            if (today != null) {
                tvWeatherHighLow.text = "最高 ${today.high}°  最低 ${today.low}°"
            } else {
                tvWeatherHighLow.text = ""
            }
            tvWeatherUpdate.text = if (now.updateTime.isNotEmpty()) "更新于 ${now.updateTime}" else ""
        } else {
            tvWeatherCity.text = state.cityName.ifEmpty { "天气" }
            tvWeatherDesc.text = if (state.error != null) "加载失败" else "加载中…"
            tvWeatherTemp.text = "--°"
            tvWeatherHighLow.text = ""
            tvWeatherUpdate.text = ""
        }
    }

    private fun openWeatherEntry() {
        val vendorWeatherPackages = listOf(
            "com.miui.weather2",
            "com.huawei.android.totemweather",
            "com.oppo.weather",
            "com.vivo.weather"
        )
        val vendorIntent = vendorWeatherPackages
            .asSequence()
            .mapNotNull { packageManager.getLaunchIntentForPackage(it) }
            .firstOrNull()
        if (vendorIntent != null) {
            startActivity(vendorIntent)
            return
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.weather_fallback_url)))
        runCatching {
            startActivity(browserIntent)
        }.onSuccess {
            Toast.makeText(this, getString(R.string.weather_fallback_notice), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, getString(R.string.weather_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAppOrder(items: List<HomeAppItem>) {
        launcherPreferences.saveAppOrder(
            items.filter { it.type == HomeAppItem.Type.APP }.map { it.packageName }
        )
    }

    private fun showCaregiverEntryDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.home_caregiver_dialog_title)
            .setMessage(R.string.home_caregiver_dialog_message)
            .setPositiveButton(R.string.home_caregiver_dialog_confirm) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.home_caregiver_dialog_cancel, null)
            .show()
    }

    private fun handleAppClick(item: HomeAppItem) {
        when (item.type) {
            HomeAppItem.Type.APP -> {
                val intent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.open_app_failed, item.appName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            HomeAppItem.Type.PHONE -> startActivity(Intent(this, PhoneContactActivity::class.java))
            HomeAppItem.Type.WECHAT_VIDEO -> startActivity(
                Intent(this, VideoCallActivity::class.java)
            )
            HomeAppItem.Type.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            HomeAppItem.Type.ADD -> startActivity(Intent(this, AppManageActivity::class.java))
        }
    }






    private fun handleAppLongClick(item: HomeAppItem): Boolean {
        if (item.type == HomeAppItem.Type.APP) {
            showRemoveDialog(item)
            return true
        }
        return false
    }

    private fun showRemoveDialog(item: HomeAppItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_remove_app, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            getString(R.string.remove_app_message, item.appName)
        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_confirm)
            .setOnClickListener {
                dialog.dismiss()
                removeApp(item.packageName)
            }
        dialog.show()
    }

    private fun removeApp(packageName: String) {
        launcherPreferences.setPackageSelected(packageName, false)
        Toast.makeText(this, getString(R.string.remove_success), Toast.LENGTH_SHORT).show()
    }
}
