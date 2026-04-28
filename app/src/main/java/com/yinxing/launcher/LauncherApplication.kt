package com.yinxing.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatDelegate
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.perf.traceSection
import com.yinxing.launcher.data.home.LauncherAppRepository
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.incoming.IncomingCallForegroundService
import com.yinxing.launcher.feature.incoming.PhoneCallReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var phoneCallReceiverRegistered = false
    private val phoneCallReceiver = PhoneCallReceiver()

    override fun onCreate() {
        super.onCreate()
        traceSection(LauncherTraceNames.APP_INIT) {
            applyDarkModePreference()
            registerPhoneCallReceiver()
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
    }

    private fun registerPhoneCallReceiver() {
        if (phoneCallReceiverRegistered) return
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            priority = 999
        }
        registerReceiver(phoneCallReceiver, filter)
        phoneCallReceiverRegistered = true
    }

    override fun onTerminate() {
        if (phoneCallReceiverRegistered) {
            unregisterReceiver(phoneCallReceiver)
            phoneCallReceiverRegistered = false
        }
        super.onTerminate()
    }

    private fun applyDarkModePreference() {
        val mode = LauncherPreferences.getInstance(this).getDarkMode()
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LauncherPreferences.DARK_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                LauncherPreferences.DARK_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
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
