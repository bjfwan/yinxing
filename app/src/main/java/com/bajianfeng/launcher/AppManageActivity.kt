package com.bajianfeng.launcher

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppManageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private val appList = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadInstalledApps()

        adapter = AppListAdapter(appList) { appInfo, isChecked ->
            saveAppSelection(appInfo.packageName, isChecked)
        }
        recyclerView.adapter = adapter
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)

        for (app in packages) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val appInfo = AppInfo(
                    packageName = app.packageName,
                    appName = app.loadLabel(pm).toString(),
                    icon = app.loadIcon(pm),
                    isSelected = isAppSelected(app.packageName)
                )
                appList.add(appInfo)
            }
        }

        appList.sortBy { it.appName }
    }

    private fun isAppSelected(packageName: String): Boolean {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        return prefs.getBoolean(packageName, false)
    }

    private fun saveAppSelection(packageName: String, isSelected: Boolean) {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean(packageName, isSelected).apply()
    }
}
