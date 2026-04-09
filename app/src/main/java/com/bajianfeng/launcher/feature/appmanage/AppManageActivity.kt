package com.bajianfeng.launcher.feature.appmanage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppManageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: AppListAdapter
    private lateinit var launcherPreferences: LauncherPreferences
    private val appRepository by lazy { LauncherAppRepository.getInstance(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var packageReceiverRegistered = false

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appRepository.invalidateInstalledApps()
            loadInstalledApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)

        launcherPreferences = LauncherPreferences.getInstance(this)

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.tv_empty_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        adapter = AppListAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onCheckChanged = { appInfo, isChecked ->
                saveAppSelection(appInfo.packageName, isChecked)
            }
        )
        recyclerView.adapter = adapter

        registerPackageReceiver()
        applyPerformanceMode()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        applyPerformanceMode()
    }

    override fun onDestroy() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver)
        }
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
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 6 else 20)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
        adapter.setLowPerformanceMode(lowPerformanceMode)
    }

    private fun loadInstalledApps() {
        scope.launch {
            val apps = appRepository.getInstalledApps(launcherPreferences)
            adapter.submitList(apps)
            recyclerView.isVisible = apps.isNotEmpty()
            emptyView.isVisible = apps.isEmpty()
        }
    }

    private fun saveAppSelection(packageName: String, isSelected: Boolean) {
        launcherPreferences.setPackageSelected(packageName, isSelected)
        appRepository.invalidateSelections()
        adapter.updateSelection(packageName, isSelected)
    }
}
