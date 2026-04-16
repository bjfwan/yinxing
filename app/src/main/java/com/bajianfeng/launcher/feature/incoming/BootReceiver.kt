package com.bajianfeng.launcher.feature.incoming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启动广播接收器。
 *
 * 利用 RECEIVE_BOOT_COMPLETED 权限，在设备重启后触发一次应用进程启动。
 * NotificationListenerService (WeChatIncomingCallService) 会由系统自动重新绑定，
 * 此处只需发一个无害的 Intent 唤醒进程，确保 Application.onCreate() 被执行
 * 完成预热（SP / 联系人缓存 / 应用列表缓存）。
 *
 * 注意：不在此处手动 startService，避免后台服务启动限制（Android 8+）。
 * NotificationListenerService 由系统根据权限授权状态自动管理生命周期。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "设备重启完成，唤醒应用进程以完成预热")
        // 发送一个显式广播给自己，触发进程创建和 Application.onCreate()
        // 系统会自动重新绑定 NotificationListenerService
    }
}
