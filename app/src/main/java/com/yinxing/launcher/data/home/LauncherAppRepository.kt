package com.yinxing.launcher.data.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceAndReport
import com.yinxing.launcher.common.perf.traceBegin
import com.yinxing.launcher.feature.appmanage.AppInfo
import com.yinxing.launcher.feature.home.HomeAppItem
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LauncherAppRepository(
    context: Context,
    private val appSource: LauncherAppSource = AndroidLauncherAppSource(context)
) {
    private val appContext = context.applicationContext
    private val installMutex = Mutex()
    private val homeMutex = Mutex()
    private var installedAppsCache: List<LauncherAppRecord>? = null
    private var homeItemsCache: List<HomeAppItem>? = null
    @Volatile private var installedAppsDirty = true
    @Volatile private var homeItemsDirty = true
    private val installedAppsVersion = AtomicInteger(0)
    private val homeItemsVersion = AtomicInteger(0)

    companion object {
        @Volatile
        private var instance: LauncherAppRepository? = null

        fun getInstance(context: Context): LauncherAppRepository {
            return instance ?: synchronized(this) {
                instance ?: LauncherAppRepository(context).also { instance = it }
            }
        }
    }

    suspend fun prewarmInstalledApps() {
        loadInstalledApps()
    }

    fun getStaticHomeItems(): List<HomeAppItem> {
        return buildList {
            addPrimaryBuiltInItems()
            addSecondaryBuiltInItems()
        }
    }

    suspend fun getInstalledApps(preferences: LauncherPreferences): List<AppInfo> = withContext(Dispatchers.IO) {
        loadInstalledApps().map { app ->
            AppInfo(
                packageName = app.packageName,
                appName = app.appName,
                isSelected = preferences.isPackageSelected(app.packageName)
            )
        }
    }

    suspend fun getHomeItems(preferences: LauncherPreferences): List<HomeAppItem> = withContext(Dispatchers.IO) {
        traceBegin(LauncherTraceNames.HOME_APP_LIST_LOAD)
        homeMutex.withLock {
            if (!homeItemsDirty) {
                homeItemsCache?.let {
                    traceAndReport(appContext, LauncherTraceNames.HOME_APP_LIST_LOAD)
                    return@withContext it
                }
            }

            val requestVersion = homeItemsVersion.get()
            val selectedPackages = preferences.getSelectedPackages()
            val selectedApps = loadSelectedHomeApps(selectedPackages)
                .map { app ->
                    HomeAppItem(
                        packageName = app.packageName,
                        appName = app.appName,
                        type = HomeAppItem.Type.APP
                    )
                }

            val selectedAppsByPackage = selectedApps.associateBy { it.packageName }
            val orderedApps = HomeAppOrderPolicy.orderApps(
                selectedApps.map { OrderedApp(it.packageName, it.appName) },
                preferences.getAppOrder()
            )

            val items = buildList {
                addPrimaryBuiltInItems()
                orderedApps.forEach { app ->
                    selectedAppsByPackage[app.packageName]?.let(::add)
                }
                addSecondaryBuiltInItems()
            }

            preferences.syncAppOrder(orderedApps.map { it.packageName })

            if (requestVersion == homeItemsVersion.get()) {
                homeItemsCache = items
                homeItemsDirty = false
            }
            traceAndReport(appContext, LauncherTraceNames.HOME_APP_LIST_LOAD)
            items
        }
    }

    fun invalidateInstalledApps() {
        installedAppsVersion.incrementAndGet()
        homeItemsVersion.incrementAndGet()
        installedAppsDirty = true
        homeItemsDirty = true
        MediaThumbnailLoader.clearIconCache(appContext)
    }

    fun invalidateSelections() {
        homeItemsVersion.incrementAndGet()
        homeItemsDirty = true
    }

    private suspend fun loadInstalledApps(): List<LauncherAppRecord> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            if (!installedAppsDirty) {
                installedAppsCache?.let { return@withContext it }
            }

            val requestVersion = installedAppsVersion.get()
            val installedApps = appSource.loadInstalledApps()
                .distinctBy { it.packageName }
                .sortedBy { it.appName }

            if (requestVersion == installedAppsVersion.get()) {
                installedAppsCache = installedApps
                installedAppsDirty = false
            }
            installedApps
        }
    }

    private suspend fun loadSelectedHomeApps(packageNames: Set<String>): List<LauncherAppRecord> {
        if (packageNames.isEmpty()) {
            return emptyList()
        }
        val directApps = appSource.loadSelectedApps(packageNames)
            .distinctBy { it.packageName }
        val directPackages = directApps.mapTo(hashSetOf()) { it.packageName }
        if (directPackages.size == packageNames.size) {
            return directApps
        }
        return directApps + loadInstalledApps()
            .filter { it.packageName in packageNames && it.packageName !in directPackages }
    }

    private fun MutableList<HomeAppItem>.addPrimaryBuiltInItems() {
        add(
            HomeAppItem(
                packageName = "phone",
                appName = appContext.getString(R.string.home_item_phone),
                type = HomeAppItem.Type.PHONE,
                iconResId = android.R.drawable.ic_menu_myplaces
            )
        )
        add(
            HomeAppItem(
                packageName = "wechat_video",
                appName = appContext.getString(R.string.home_item_wechat_video),
                type = HomeAppItem.Type.WECHAT_VIDEO,
                iconResId = android.R.drawable.ic_menu_call
            )
        )
    }

    private fun MutableList<HomeAppItem>.addSecondaryBuiltInItems() {
        add(
            HomeAppItem(
                packageName = "add",
                appName = appContext.getString(R.string.home_item_add),
                type = HomeAppItem.Type.ADD,
                iconResId = android.R.drawable.ic_input_add
            )
        )
    }
}

private class AndroidLauncherAppSource(context: Context) : LauncherAppSource {
    private val appContext = context.applicationContext

    override suspend fun loadInstalledApps(): List<LauncherAppRecord> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = appContext.packageManager
        return queryLauncherActivities(packageManager, launcherIntent)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                if (packageName == appContext.packageName) {
                    return@mapNotNull null
                }
                LauncherAppRecord(
                    packageName = packageName,
                    appName = activityInfo.applicationInfo.loadLabel(packageManager).toString()
                )
            }
    }

    override suspend fun loadSelectedApps(packageNames: Set<String>): List<LauncherAppRecord> {
        if (packageNames.isEmpty()) {
            return emptyList()
        }
        val packageManager = appContext.packageManager
        return packageNames.mapNotNull { packageName ->
            if (packageName == appContext.packageName) {
                return@mapNotNull null
            }
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
            val activityInfo = queryLauncherActivities(packageManager, launcherIntent)
                .firstOrNull()
                ?.activityInfo
                ?: return@mapNotNull null
            LauncherAppRecord(
                packageName = activityInfo.packageName,
                appName = activityInfo.applicationInfo.loadLabel(packageManager).toString()
            )
        }
    }

    private fun queryLauncherActivities(
        packageManager: PackageManager,
        launcherIntent: Intent
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, 0)
    }
}
