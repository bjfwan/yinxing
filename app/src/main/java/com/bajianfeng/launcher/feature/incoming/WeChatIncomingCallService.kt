package com.bajianfeng.launcher.feature.incoming

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * 微信来电通知监听服务。
 *
 * 微信视频/语音来电会以系统通知形式出现，通知上带有"接受"/"拒绝"两个 Action。
 * 本服务拦截该通知，解析来电方名称和 Action，通过 LocalBroadcast 通知 IncomingCallActivity。
 *
 * 通知识别规则（微信 8.x）：
 *   - packageName == "com.tencent.mm"
 *   - 通知 category == Notification.CATEGORY_CALL 或 title/text 含"视频通话"/"语音通话"/"邀请您"
 *   - 通知带有至少一个 Action（接听/拒绝）
 */
class WeChatIncomingCallService : NotificationListenerService() {

    companion object {
        private const val TAG = "WeChatCallListener"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

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
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        isConnected = false
        Log.d(TAG, "NotificationListenerService 已断开")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        if (sbn.packageName != WECHAT_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val combined = "$title $text $bigText"

        Log.d(TAG, "微信通知: title=$title text=$text")

        // 仅处理来电通知
        val isCallNotification = notification.category == android.app.Notification.CATEGORY_CALL ||
            CALL_KEYWORDS.any { combined.contains(it) }
        if (!isCallNotification) return

        val actions = notification.actions
        if (actions.isNullOrEmpty()) {
            Log.d(TAG, "来电通知无 Action，无法自动接听，仅展示来电页")
        }

        val acceptAction = actions?.firstOrNull { action ->
            val label = action.title?.toString().orEmpty()
            ACCEPT_KEYWORDS.any { label.contains(it) }
        }
        val declineAction = actions?.firstOrNull { action ->
            val label = action.title?.toString().orEmpty()
            DECLINE_KEYWORDS.any { label.contains(it) }
        }

        // 解析来电方名称：优先用 title（微信通知 title 通常是对方昵称）
        val callerName = title.takeIf { it.isNotBlank() }

        Log.d(
            TAG,
            "识别到微信来电: caller=$callerName " +
                "acceptAction=${acceptAction?.title} declineAction=${declineAction?.title}"
        )

        // 直接启动来电全屏页（LocalBroadcast 在锁屏/无前台 Activity 场景下无法接收）
        val activityIntent = IncomingCallActivity.buildLaunchIntent(
            context = this,
            callerName = callerName,
            notificationKey = sbn.key,
            acceptIntent = acceptAction?.actionIntent,
            declineIntent = declineAction?.actionIntent
        )
        startActivity(activityIntent)

        // 同时广播（用于已有前台 Activity 刷新来电信息）
        val broadcastIntent = Intent(IncomingCallBroadcast.ACTION_INCOMING).apply {
            putExtra(IncomingCallBroadcast.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY, sbn.key)
            putExtra(IncomingCallBroadcast.EXTRA_ACCEPT_INTENT, acceptAction?.actionIntent)
            putExtra(IncomingCallBroadcast.EXTRA_DECLINE_INTENT, declineAction?.actionIntent)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        if (sbn.packageName != WECHAT_PACKAGE) return

        // 通知被系统/微信撤销（接听了或超时），通知 UI 消失
        val intent = Intent(IncomingCallBroadcast.ACTION_DISMISS).apply {
            putExtra(IncomingCallBroadcast.EXTRA_NOTIFICATION_KEY, sbn.key)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "微信来电通知已移除: key=${sbn.key}")
    }
}
