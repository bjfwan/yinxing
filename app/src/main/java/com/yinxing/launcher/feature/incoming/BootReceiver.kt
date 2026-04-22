package com.yinxing.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.home.MainActivity

/**
 * 开机恢复入口。
 * 在系统开机完成或应用更新后，预创建来电通知通道，
 * 并在本应用承担桌面角色时尝试恢复到桌面首页。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        IncomingCallForegroundService.ensureNotificationChannels(context)

        val prefs = LauncherPreferences.getInstance(context)
        if (!prefs.isKioskModeEnabled() && !isDefaultLauncher(context)) {
            return
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launch)
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }
}
