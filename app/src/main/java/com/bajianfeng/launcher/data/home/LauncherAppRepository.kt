package com.bajianfeng.launcher.data.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import com.bajianfeng.launcher.feature.appmanage.AppInfo
import com.bajianfeng.launcher.feature.home.HomeAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherAppRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val cacheLock = Any()
    private var installedAppsCache: List<InstalledAppRecord>? = null
    private var homeItemsCache: List<HomeAppItem>? = null
    private var installedAppsDirty = true
    private var homeItemsDirty = true

    companion object {
        @Volatile
        private var instance: LauncherAppRepository? = null

        fun getInstance(context: Context): LauncherAppRepository {
            return instance ?: synchronized(this) {
                instance ?: LauncherAppRepository(context).also { instance = it }
            }
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
        synchronized(cacheLock) {
            if (!homeItemsDirty) {
                homeItemsCache?.let { return@withContext it }
            }
        }

        val installedApps = loadInstalledApps()
        val selectedApps = installedApps
            .filter { preferences.isPackageSelected(it.packageName) }
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
            orderedApps.forEach { app ->
                selectedAppsByPackage[app.packageName]?.let(::add)
            }
            addBuiltInItems()
        }

        preferences.syncAppOrder(orderedApps.map { it.packageName })

        synchronized(cacheLock) {
            homeItemsCache = items
            homeItemsDirty = false
        }
        items
    }

    fun invalidateInstalledApps() {
        synchronized(cacheLock) {
            installedAppsDirty = true
            homeItemsDirty = true
        }
        MediaThumbnailLoader.clearIconCache()
    }

    fun invalidateSelections() {
        synchronized(cacheLock) {
            homeItemsDirty = true
        }
    }

    private suspend fun loadInstalledApps(): List<InstalledAppRecord> = withContext(Dispatchers.IO) {
        synchronized(cacheLock) {
            if (!installedAppsDirty) {
                installedAppsCache?.let { return@withContext it }
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = appContext.packageManager
        val resolveInfos = queryLauncherActivities(packageManager, launcherIntent)
        val installedApps = resolveInfos
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                if (packageName == appContext.packageName) {
                    return@mapNotNull null
                }
                InstalledAppRecord(
                    packageName = packageName,
                    appName = activityInfo.applicationInfo.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName }

        synchronized(cacheLock) {
            installedAppsCache = installedApps
            installedAppsDirty = false
        }
        installedApps
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

    private fun MutableList<HomeAppItem>.addBuiltInItems() {
        add(
            HomeAppItem(
                packageName = "phone",
                appName = appContext.getString(R.string.home_item_phone),
                type = HomeAppItem.Type.PHONE,
                iconResId = android.R.drawable.ic_menu_call
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
        add(
            HomeAppItem(
                packageName = "settings",
                appName = appContext.getString(R.string.home_item_settings),
                type = HomeAppItem.Type.SETTINGS,
                iconResId = android.R.drawable.ic_menu_preferences
            )
        )
        add(
            HomeAppItem(
                packageName = "add",
                appName = appContext.getString(R.string.home_item_add),
                type = HomeAppItem.Type.ADD,
                iconResId = android.R.drawable.ic_input_add
            )
        )
        add(
            HomeAppItem(
                packageName = "weather",
                appName = appContext.getString(R.string.home_item_weather),
                type = HomeAppItem.Type.WEATHER,
                iconResId = android.R.drawable.ic_dialog_info
            )
        )
    }

    private data class InstalledAppRecord(
        val packageName: String,
        val appName: String
    )
}
