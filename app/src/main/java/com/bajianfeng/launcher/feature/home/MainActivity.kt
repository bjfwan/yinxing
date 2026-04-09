package com.bajianfeng.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.feature.appmanage.AppManageActivity
import com.bajianfeng.launcher.feature.phone.PhoneActivity
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
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var itemMoveCallback: ItemMoveCallback
    private val appRepository by lazy { LauncherAppRepository.getInstance(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
    private var packageReceiverRegistered = false

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

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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
        launcherPreferences.registerListener(preferenceListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, getString(R.string.home_toast_already_here), Toast.LENGTH_SHORT).show()
            }
        })

        recyclerView = findViewById(R.id.recycler_home)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(false)

        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)

        adapter = HomeAppAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onItemClick = { item -> handleAppClick(item) },
            onItemLongClick = { item -> handleAppLongClick(item) },
            onOrderChanged = { items -> saveAppOrder(items) }
        )
        recyclerView.adapter = adapter

        itemMoveCallback = ItemMoveCallback(adapter, !launcherPreferences.isLowPerformanceModeEnabled())
        val touchHelper = ItemTouchHelper(itemMoveCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.setTouchHelper(touchHelper)

        registerPackageReceiver()
        applyPerformanceMode()
        refreshApps()
    }

    override fun onResume() {
        super.onResume()
        updateTime()
        scheduleNextTimeUpdate()
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
        val interval = if (launcherPreferences.isLowPerformanceModeEnabled()) 60_000L else 1_000L
        val now = System.currentTimeMillis()
        val delay = interval - (now % interval)
        handler.postDelayed(updateTimeRunnable, if (delay == 0L) interval else delay)
    }

    private fun updateTime() {
        val now = Calendar.getInstance().time
        tvTime.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now)
    }

    private fun refreshApps() {
        scope.launch {
            adapter.submitList(appRepository.getHomeItems(launcherPreferences))
        }
    }

    private fun saveAppOrder(items: List<HomeAppItem>) {
        launcherPreferences.saveAppOrder(
            items.filter { it.type == HomeAppItem.Type.APP }.map { it.packageName }
        )
    }

    private fun handleAppClick(item: HomeAppItem) {
        when (item.type) {
            HomeAppItem.Type.APP -> {
                val intent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.open_app_failed, item.appName), Toast.LENGTH_SHORT).show()
                }
            }
            HomeAppItem.Type.PHONE -> startActivity(Intent(this, PhoneActivity::class.java))
            HomeAppItem.Type.WECHAT_VIDEO -> startActivity(Intent(this, VideoCallActivity::class.java))
            HomeAppItem.Type.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            HomeAppItem.Type.ADD -> startActivity(Intent(this, AppManageActivity::class.java))
            HomeAppItem.Type.WEATHER -> openWeather()
        }
    }

    private fun openWeather() {
        val weatherPackages = listOf(
            "com.google.android.googlequicksearchbox",
            "com.miui.weather2",
            "com.huawei.android.totemweather",
            "com.oppo.weather",
            "com.vivo.weather"
        )

        for (pkg in weatherPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, getString(R.string.weather_fallback_url).toUri())
        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(browserIntent)
            Toast.makeText(this, getString(R.string.weather_fallback_notice), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.weather_not_available), Toast.LENGTH_SHORT).show()
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
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            getString(R.string.remove_app_message, item.appName)

        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_confirm).setOnClickListener {
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
