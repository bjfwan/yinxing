package com.yinxing.launcher.feature.appmanage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView

import com.yinxing.launcher.common.ui.FontScaleActivity
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppManageActivity : FontScaleActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: AppListAdapter
    private lateinit var launcherPreferences: LauncherPreferences
    private val appRepository by lazy { LauncherAppRepository.getInstance(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var packageReceiverRegistered = false
    private var loadAppsJob: kotlinx.coroutines.Job? = null

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appRepository.invalidateInstalledApps()
            loadInstalledApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manage)
        applySystemInsets()

        launcherPreferences = LauncherPreferences.getInstance(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
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

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.app_manage_root)
        val baseTopPadding = root.paddingTop
        val baseBottomPadding = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                top = baseTopPadding + bars.top,
                bottom = baseBottomPadding + bars.bottom
            )
            insets
        }
    }

    private fun loadInstalledApps() {
        loadAppsJob?.cancel()
        loadAppsJob = scope.launch {
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
        LobsterClient.reportUsage(
            this,
            LobsterSettingEventFactory.homeAppSelectionChanged(isSelected).withTrace(LobsterTrace.newId())
        )
    }
}
