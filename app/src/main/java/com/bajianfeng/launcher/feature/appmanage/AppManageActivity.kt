package com.bajianfeng.launcher.feature.appmanage

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.*

class AppManageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: AppListAdapter
    private val appList = mutableListOf<AppInfo>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var launcherPreferences: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)

        launcherPreferences = LauncherPreferences.getInstance(this)

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.tv_empty_apps)
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
        val selfPackage = packageName
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launcherIntent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }

                resolveInfos
                    .mapNotNull { resolveInfo ->
                        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                        val packageName = activityInfo.packageName
                        if (packageName == selfPackage) {
                            return@mapNotNull null
                        }
                        val applicationInfo = activityInfo.applicationInfo
                        AppInfo(
                            packageName = packageName,
                            appName = applicationInfo.loadLabel(pm).toString(),
                            icon = applicationInfo.loadIcon(pm),
                            isSelected = launcherPreferences.isPackageSelected(packageName)
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.appName }
            }
            appList.clear()
            appList.addAll(apps)
            adapter.notifyDataSetChanged()
            recyclerView.isVisible = apps.isNotEmpty()
            emptyView.isVisible = apps.isEmpty()
        }
    }

    private fun saveAppSelection(packageName: String, isSelected: Boolean) {
        launcherPreferences.setPackageSelected(packageName, isSelected)
    }
}
