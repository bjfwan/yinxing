package com.yinxing.launcher.feature.setup

import android.content.Context
import androidx.core.content.edit

class FamilySetupPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        preferences.edit { putBoolean(KEY_COMPLETED, true) }
    }

    fun shouldLaunchAutomatically(): Boolean {
        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        return if (packageInfo == null) {
            !isCompleted()
        } else {
            shouldLaunchAutomatically(packageInfo.firstInstallTime, packageInfo.lastUpdateTime)
        }
    }

    internal fun shouldLaunchAutomatically(
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ): Boolean {
        if (isCompleted()) return false
        if (preferences.contains(KEY_AUTO_LAUNCH_REQUIRED)) {
            return preferences.getBoolean(KEY_AUTO_LAUNCH_REQUIRED, false)
        }
        val required = lastUpdateTime <= firstInstallTime
        preferences.edit { putBoolean(KEY_AUTO_LAUNCH_REQUIRED, required) }
        return required
    }

    companion object {
        const val PREFS_NAME = "family_setup"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_AUTO_LAUNCH_REQUIRED = "auto_launch_required"
    }
}
