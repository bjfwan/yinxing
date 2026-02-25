package com.bajianfeng.launcher

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class AppManageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private val appList = mutableListOf<AppInfo>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)

        adapter = AppListAdapter(appList) { appInfo, isChecked ->
            saveAppSelection(appInfo.packageName, isChecked)
        }
        recyclerView.adapter = adapter

        loadInstalledApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadInstalledApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
                pm.getInstalledApplications(0)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { app ->
                        AppInfo(
                            packageName = app.packageName,
                            appName = app.loadLabel(pm).toString(),
                            icon = app.loadIcon(pm),
                            isSelected = prefs.getBoolean(app.packageName, false)
                        )
                    }
                    .sortedBy { it.appName }
            }
            appList.clear()
            appList.addAll(apps)
            adapter.notifyDataSetChanged()
        }
    }

    private fun saveAppSelection(packageName: String, isSelected: Boolean) {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean(packageName, isSelected).commit()
    }
}
