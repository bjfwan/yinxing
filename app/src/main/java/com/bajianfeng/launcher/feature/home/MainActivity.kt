package com.bajianfeng.launcher.feature.home

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.HomeAppOrderPolicy
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.data.home.OrderedApp
import com.bajianfeng.launcher.feature.appmanage.AppManageActivity
import com.bajianfeng.launcher.feature.phone.PhoneActivity
import com.bajianfeng.launcher.feature.videocall.VideoCallActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HomeAppAdapter
    private val appList = mutableListOf<HomeAppItem>()
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var launcherPreferences: LauncherPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launcherPreferences = LauncherPreferences.getInstance(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, "已经是桌面了", Toast.LENGTH_SHORT).show()
            }
        })

        recyclerView = findViewById(R.id.recycler_home)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(false)
        recyclerView.setItemViewCacheSize(10)

        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)

        loadApps()

        adapter = HomeAppAdapter(
            appList,
            onItemClick = { item -> handleAppClick(item) },
            onItemLongClick = { item -> handleAppLongClick(item) },
            onOrderChanged = { saveAppOrder() }
        )
        recyclerView.adapter = adapter

        val callback = ItemMoveCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.setTouchHelper(touchHelper)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        adapter.notifyDataSetChanged()
        updateTime()
        handler.post(updateTimeRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
    }

    private fun updateTime() {
        val now = Calendar.getInstance().time
        tvTime.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now)
    }

    private fun loadApps() {
        appList.clear()

        val pm = packageManager
        val selectedApps = launcherPreferences.getSelectedPackages().mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                HomeAppItem(
                    packageName = packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm),
                    type = HomeAppItem.Type.APP
                )
            } catch (_: Exception) {
                null
            }
        }

        val selectedAppsByPackage = selectedApps.associateBy { it.packageName }
        val orderedApps = HomeAppOrderPolicy.orderApps(
            selectedApps.map { OrderedApp(it.packageName, it.appName) },
            launcherPreferences.getAppOrder()
        )

        orderedApps.forEach { app ->
            selectedAppsByPackage[app.packageName]?.let(appList::add)
        }

        launcherPreferences.syncAppOrder(orderedApps.map { it.packageName })

        appList.add(HomeAppItem("phone", "电话", getDrawable(android.R.drawable.ic_menu_call)!!, HomeAppItem.Type.PHONE))
        appList.add(HomeAppItem("wechat_video", "微信视频", getDrawable(android.R.drawable.ic_menu_call)!!, HomeAppItem.Type.WECHAT_VIDEO))
        appList.add(HomeAppItem("settings", "设置", getDrawable(android.R.drawable.ic_menu_preferences)!!, HomeAppItem.Type.SETTINGS))
        appList.add(HomeAppItem("add", "添加", getDrawable(android.R.drawable.ic_input_add)!!, HomeAppItem.Type.ADD))
        appList.add(HomeAppItem("weather", "天气", getDrawable(android.R.drawable.ic_dialog_info)!!, HomeAppItem.Type.WEATHER))
    }

    private fun saveAppOrder() {
        launcherPreferences.saveAppOrder(
            appList
                .filter { it.type == HomeAppItem.Type.APP }
                .map { it.packageName }
        )
    }

    private fun handleAppClick(item: HomeAppItem) {
        when (item.type) {
            HomeAppItem.Type.APP -> {
                val intent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "无法打开${item.appName}", Toast.LENGTH_SHORT).show()
                }
            }
            HomeAppItem.Type.PHONE -> startActivity(Intent(this, PhoneActivity::class.java))
            HomeAppItem.Type.WECHAT_VIDEO -> startActivity(Intent(this, VideoCallActivity::class.java))
            HomeAppItem.Type.SETTINGS -> {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
                }
            }
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

        Toast.makeText(this, "未找到天气应用", Toast.LENGTH_SHORT).show()
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
            "确定要从桌面移除 ${item.appName} 吗？"

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
        loadApps()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "已移除", Toast.LENGTH_SHORT).show()
    }
}
