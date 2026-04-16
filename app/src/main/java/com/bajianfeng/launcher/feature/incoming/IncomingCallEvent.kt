package com.bajianfeng.launcher.feature.incoming

/**
 * 微信来电事件，由 WeChatIncomingCallService 广播，IncomingCallActivity 接收。
 *
 * 接听/拒绝方案：传递通知 Action 下标（acceptActionIndex / declineActionIndex），
 * 由 Activity 回调 WeChatIncomingCallService.performAction() 在服务进程内触发，
 * 避免跨进程 PendingIntent 被微信保护拦截的问题。
 */
data class IncomingCallEvent(
    val callerName: String?,
    val notificationKey: String,
    /** 接听 Action 在通知 actions 数组中的下标，-1 表示未找到 */
    val acceptActionIndex: Int,
    /** 拒绝 Action 在通知 actions 数组中的下标，-1 表示未找到 */
    val declineActionIndex: Int
)

object IncomingCallBroadcast {
    const val ACTION_INCOMING = "com.bajianfeng.launcher.WECHAT_INCOMING_CALL"
    const val ACTION_DISMISS  = "com.bajianfeng.launcher.WECHAT_CALL_DISMISS"

    const val EXTRA_CALLER_NAME          = "caller_name"
    const val EXTRA_NOTIFICATION_KEY     = "notification_key"
    const val EXTRA_ACCEPT_ACTION_INDEX  = "accept_action_index"
    const val EXTRA_DECLINE_ACTION_INDEX = "decline_action_index"

    // 兼容旧字段名（不再使用，保留常量避免编译错误）
    @Deprecated("改用 EXTRA_ACCEPT_ACTION_INDEX")
    const val EXTRA_ACCEPT_INTENT  = "accept_intent"
    @Deprecated("改用 EXTRA_DECLINE_ACTION_INDEX")
    const val EXTRA_DECLINE_INTENT = "decline_intent"
}
