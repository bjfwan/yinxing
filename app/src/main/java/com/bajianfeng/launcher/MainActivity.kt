package com.bajianfeng.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HomeAppAdapter
    private val appList = mutableListOf<HomeAppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, "已经是桌面了", Toast.LENGTH_SHORT).show()
            }
        })

        recyclerView = findViewById(R.id.recycler_home)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        loadApps()

        adapter = HomeAppAdapter(appList) { item ->
            handleAppClick(item)
        }
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        appList.clear()

        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val pm = packageManager

        val allPrefs = prefs.all
        for ((packageName, isSelected) in allPrefs) {
            if (isSelected == true) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    appList.add(
                        HomeAppItem(
                            packageName = packageName,
                            appName = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            type = HomeAppItem.Type.APP
                        )
                    )
                } catch (e: Exception) {
                }
            }
        }

        appList.sortBy { it.appName }

        appList.add(
            HomeAppItem(
                packageName = "settings",
                appName = "设置",
                icon = getDrawable(android.R.drawable.ic_menu_preferences)!!,
                type = HomeAppItem.Type.SETTINGS
            )
        )

        appList.add(
            HomeAppItem(
                packageName = "add",
                appName = "添加",
                icon = getDrawable(android.R.drawable.ic_input_add)!!,
                type = HomeAppItem.Type.ADD
            )
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
            HomeAppItem.Type.SETTINGS -> {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
                }
            }
            HomeAppItem.Type.ADD -> {
                startActivity(Intent(this, AppManageActivity::class.java))
            }
        }
    }
}
