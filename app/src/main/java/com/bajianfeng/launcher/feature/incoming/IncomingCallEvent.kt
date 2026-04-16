package com.bajianfeng.launcher.feature.incoming

/**
 * 微信来电事件，由 WeChatIncomingCallService 广播，IncomingCallActivity 接收。
 */
data class IncomingCallEvent(
    /** 通知里解析到的来电方名称，可能为 null（无法解析时）*/
    val callerName: String?,
    /** 通知 key，用于 cancelNotification */
    val notificationKey: String,
    /** 接听 PendingIntent，来自通知 Action，null 表示无法自动接听 */
    val acceptPendingIntent: android.app.PendingIntent?,
    /** 拒绝 PendingIntent，来自通知 Action，null 表示无法自动拒接 */
    val declinePendingIntent: android.app.PendingIntent?
)

object IncomingCallBroadcast {
    const val ACTION_INCOMING = "com.bajianfeng.launcher.WECHAT_INCOMING_CALL"
    const val ACTION_DISMISS = "com.bajianfeng.launcher.WECHAT_CALL_DISMISS"

    const val EXTRA_CALLER_NAME = "caller_name"
    const val EXTRA_NOTIFICATION_KEY = "notification_key"
    const val EXTRA_ACCEPT_INTENT = "accept_intent"
    const val EXTRA_DECLINE_INTENT = "decline_intent"
}
