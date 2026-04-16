package com.bajianfeng.launcher.feature.incoming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bajianfeng.launcher.R

/**
 * 微信来电通知监听服务（升级版）。
 *
 * 接听/挂断方案：
 *   使用 NotificationListenerService.performNotificationAction(key, actionIndex)
 *   在服务内部直接触发通知 Action，绕开跨进程 PendingIntent 被微信拦截的问题。
 *
 * 来电识别规则（微信 8.x）：
 *   - packageName == "com.tencent.mm"
 *   - 通知 category == CATEGORY_CALL 或 title/text 含关键词
 *   - 通知带有至少一个 Action
 */
class WeChatIncomingCallService : NotificationListenerService() {

    companion object {
        private const val TAG = "WeChatCallListener"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private const val FOREGROUND_CHANNEL_ID = "wechat_listener_keep_alive"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        /** 微信来电通知的关键词，满足任意一个即视为来电 */
        private val CALL_KEYWORDS = listOf(
            "视频通话", "语音通话", "邀请您", "邀请你", "发起了通话"
        )

        /** 接听 Action 文本关键词 */
        private val ACCEPT_KEYWORDS = listOf("接受", "接听", "接通", "Accept")

        /** 拒绝 Action 文本关键词 */
        private val DECLINE_KEYWORDS = listOf("拒绝", "挂断", "拒接", "Decline", "忽略")

        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        private var instance: WeChatIncomingCallService? = null

        /**
         * 通过 NotificationListenerService 直接触发通知 Action（不依赖 PendingIntent 跨进程）。
         * 这是接听/挂断微信来电最可靠的方式。
         *
         * @param key  通知 key（来自 StatusBarNotification.key）
         * @param keywords  匹配 Action title 的关键词列表
         */
        fun performAction(key: String, keywords: List<String>): Boolean {
            val svc = instance ?: return false
            return svc.performNotificationAction(key, keywords)
        }

        /** 取消指定 key 的通知（用于拒绝时清除通知栏残留） */
        fun cancelNotification(key: String) {
            instance?.cancelNotification(key)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected = true
        Log.d(TAG, "NotificationListenerService 已连接")
        startForegroundIfNeeded()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        isConnected = false
        Log.d(TAG, "NotificationListenerService 已断开")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        isConnected = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        if (sbn.packageName != WECHAT_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text  = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val combined = "$title $text $bigText"

        Log.d(TAG, "微信通知: title=$title text=$text")

        // 仅处理来电通知
        val isCallNotification = notification.category == Notification.CATEGORY_CALL ||
            CALL_KEYWORDS.any { combined.contains(it) }
        if (!isCallNotification) return

        val actions = notification.actions
        if (actions.isNullOrEmpty()) {
            Log.d(TAG, "来电通知无 Action，无法操作")
        }

        // 解析接听/拒绝 Action 下标（供后续 performNotificationAction 使用）
        var acceptIndex = -1
        var declineIndex = -1
        actions?.forEachIndexed { index, action ->
            val label = action.title?.toString().orEmpty()
            when {
                acceptIndex == -1 && ACCEPT_KEYWORDS.any { label.contains(it) } -> acceptIndex = index
                declineIndex == -1 && DECLINE_KEYWORDS.any { label.contains(it) } -> declineIndex = index
            }
        }

        // 解析来电方名称
        // 微信通知 title 通常就是对方昵称；若 title 是"微信"或空则尝试 text
        val callerName = resolveCallerName(title, text)

        Log.d(
            TAG,
            "识别到微信来电: caller=$callerName acceptIndex=$acceptIndex declineIndex=$declineIndex"
        )

        // 直接启动来电全屏页（LocalBroadcast 在锁屏/无前台时无法接收）
        val activityIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            notificationKey = sbn.key,
            acceptActionIndex = acceptIndex,
            declineActionIndex = declineIndex
        )
        startActivity(activityIntent)

        // 同时广播（用于已有前台 Activity 刷新来电信息）
        val broadcastIntent = Intent(IncomingCallBroadcast.ACTION_INCOMING).apply {
            putExtra(IncomingCallBroadcast.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY, sbn.key)
            putExtra(IncomingCallBroadcast.EXTRA_ACCEPT_ACTION_INDEX, acceptIndex)
            putExtra(IncomingCallBroadcast.EXTRA_DECLINE_ACTION_INDEX, declineIndex)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        if (sbn.packageName != WECHAT_PACKAGE) return

        val intent = Intent(IncomingCallBroadcast.ACTION_DISMISS).apply {
            putExtra(IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY, sbn.key)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "微信来电通知已移除: key=${sbn.key}")
    }

    // ── 来电方名称解析 ────────────────────────────────────────────────────────

    /**
     * 解析来电方显示名。
     * 微信通知规律：
     *   - title = 对方昵称（正常情况）
     *   - 如果 title = "微信" 或空，则从 text 中提取（"XX 邀请您…"）
     */
    private fun resolveCallerName(title: String, text: String): String? {
        if (title.isNotBlank() && title != "微信" && title != "WeChat") {
            return title
        }
        // 从 text 中提取：格式通常为"昵称 邀请您视频通话"
        if (text.isNotBlank()) {
            val spaceIdx = text.indexOf(' ')
            if (spaceIdx > 0) return text.substring(0, spaceIdx)
        }
        return title.takeIf { it.isNotBlank() }
    }

    // ── 通知 Action 操作 ──────────────────────────────────────────────────────

    /**
     * 在服务进程内查找并触发通知 Action，无需跨进程 PendingIntent。
     * 通过 Notification.Action.actionIntent.send() 在 listener 进程中发送，
     * 系统会授予与 NotificationListenerService 同等的权限。
     */
    private fun performNotificationAction(notificationKey: String, keywords: List<String>): Boolean {
        // 先从活跃通知中找到对应 sbn
        val sbn = runCatching { activeNotifications }.getOrNull()
            ?.firstOrNull { it.key == notificationKey }
        if (sbn == null) {
            Log.w(TAG, "performNotificationAction: 未找到通知 key=$notificationKey，尝试直接发送")
            return false
        }
        val actions = sbn.notification?.actions
        if (actions.isNullOrEmpty()) {
            Log.w(TAG, "performNotificationAction: 通知无 Action")
            return false
        }
        val action = actions.firstOrNull { a ->
            val label = a.title?.toString().orEmpty()
            keywords.any { label.contains(it) }
        }
        if (action == null) {
            Log.w(TAG, "performNotificationAction: 未找到匹配 Action，keywords=$keywords")
            return false
        }
        return runCatching {
            action.actionIntent.send()
            Log.d(TAG, "performNotificationAction: 已触发 Action '${action.title}'")
            true
        }.getOrElse { e ->
            Log.e(TAG, "performNotificationAction 失败: ${e.message}", e)
            false
        }
    }

    // ── 前台服务保活 ──────────────────────────────────────────────────────────

    private fun startForegroundIfNeeded() {
        runCatching {
            createNotificationChannelIfNeeded()
            val notification = buildKeepAliveNotification()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            Log.d(TAG, "已提升为前台服务")
        }.onFailure { e ->
            Log.w(TAG, "提升前台服务失败: ${e.message}")
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(FOREGROUND_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "来电监听（后台保活）",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "保持微信来电接收服务在后台运行，不会弹出提醒"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildKeepAliveNotification(): android.app.Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = if (contentIntent != null) {
            PendingIntent.getActivity(this, 0, contentIntent, pendingFlags)
        } else null

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("老人桌面运行中")
            .setContentText("来电提醒服务已开启")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }
}
