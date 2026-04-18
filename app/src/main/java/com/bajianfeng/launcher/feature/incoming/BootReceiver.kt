package com.bajianfeng.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bajianfeng.launcher.feature.home.MainActivity

/**
 * 开机自启广播接收器。
 * 系统发出 BOOT_COMPLETED 后，自动把桌面拉起到前台，
 * 确保老人开机后第一眼看到的就是本 Launcher，而不是厂商桌面。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launch)
        }
    }
}
