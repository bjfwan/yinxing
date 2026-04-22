package com.yinxing.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.incoming.IncomingCallForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        IncomingCallForegroundService.ensureNotificationChannels(this)

        // 在后台线程预热 SharedPreferences 和应用列表，
        // 避免首次打开 MainActivity 时在主线程同步读取
        appScope.launch {

            // 预热 LauncherPreferences（触发 SP 文件首次 mmap 加载）
            LauncherPreferences.getInstance(this@LauncherApplication)
            // 预热应用仓库（PackageManager 查询耗时，提前缓存）
            runCatching {
                val prefs = LauncherPreferences.getInstance(this@LauncherApplication)
                LauncherAppRepository.getInstance(this@LauncherApplication)
                    .getInstalledApps(prefs)
            }
            // 预热联系人缓存
            runCatching {
                ContactManager.getInstance(this@LauncherApplication).getContacts()
            }
            Log.d("LauncherApp", "预热完成（SharedPreferences + AppList + Contacts）")
        }
    }

    /**
     * 系统内存紧张时主动释放图标 LRU 缓存。
     *
     * 触发时机：
     * - TRIM_MEMORY_RUNNING_MODERATE（前台，系统内存略紧）：释放部分
     * - TRIM_MEMORY_RUNNING_LOW / CRITICAL（前台，系统内存严重不足）：全量释放
     * - TRIM_MEMORY_UI_HIDDEN（应用退到后台）：适当释放
     * - TRIM_MEMORY_BACKGROUND / MODERATE / COMPLETE（后台）：全量释放
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // 系统内存略紧，但应用在前台——不强制清空，等 LRU 自行淘汰
                Log.d("LauncherApp", "onTrimMemory: RUNNING_MODERATE，暂不主动清理")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 系统内存严重不足，主动清空图标缓存
                MediaThumbnailLoader.clearIconCache()
                Log.d("LauncherApp", "onTrimMemory: RUNNING_LOW/CRITICAL，已清空图标缓存")
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // 应用退到后台，释放 UI 相关缓存
                MediaThumbnailLoader.clearIconCache()
                Log.d("LauncherApp", "onTrimMemory: UI_HIDDEN，已清空图标缓存")
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // 在后台被系统评级要求释放内存
                MediaThumbnailLoader.clearIconCache()
                Log.d("LauncherApp", "onTrimMemory: BACKGROUND/MODERATE/COMPLETE，已清空图标缓存")
            }
        }
    }
}
