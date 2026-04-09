package com.bajianfeng.launcher.feature.appmanage

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.*

class AppManageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private val appList = mutableListOf<AppInfo>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var launcherPreferences: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)

        launcherPreferences = LauncherPreferences.getInstance(this)

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
                pm.getInstalledApplications(0)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { app ->
                        AppInfo(
                            packageName = app.packageName,
                            appName = app.loadLabel(pm).toString(),
                            icon = app.loadIcon(pm),
                            isSelected = launcherPreferences.isPackageSelected(app.packageName)
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
        launcherPreferences.setPackageSelected(packageName, isSelected)
    }
}
