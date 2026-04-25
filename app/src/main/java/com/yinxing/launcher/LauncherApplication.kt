package com.yinxing.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.feature.incoming.IncomingCallForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                IncomingCallForegroundService.ensureNotificationChannels(this)
                appScope.launch {
                    runCatching {
                        LauncherAppRepository.getInstance(this@LauncherApplication).prewarmInstalledApps()
                    }
                }
            },
            3_000L
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MediaThumbnailLoader.clearIconCache()
        }
    }
}
