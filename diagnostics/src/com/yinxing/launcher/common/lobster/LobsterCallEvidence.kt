package com.yinxing.launcher.common.lobster

import org.json.JSONObject

/** 只允许布尔证据与有限枚举；不接收姓名、包名、通知或节点文字。 */
data class LobsterCallEvidence(
    val answerSent: Boolean = false,
    val callWindowSeen: Boolean = false,
    val callAudioSeen: Boolean = false,
    val connectionConfirmed: Boolean = false,
    val returnSessionRetained: Boolean = false,
    val completeCalled: Boolean = false,
    val startActivityCalled: Boolean = false,
    val foreground: LobsterForeground = LobsterForeground.UNKNOWN,
    val reason: LobsterCallReason = LobsterCallReason.NONE
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("answer_sent", answerSent)
        put("call_window_seen", callWindowSeen)
        put("call_audio_seen", callAudioSeen)
        put("connection_confirmed", connectionConfirmed)
        put("return_session_retained", returnSessionRetained)
        put("complete_called", completeCalled)
        put("start_activity_called", startActivityCalled)
        put("foreground", foreground.name.lowercase())
        put("reason", reason.name.lowercase())
    }

    fun safeLine(): String = toJson().toString()
}

enum class LobsterForeground { LAUNCHER, WECHAT, OTHER, UNKNOWN }
enum class LobsterCallReason {
    NONE, DISABLED, USER_LEFT, SERVICE_STOPPED, REPLACED, EXPIRED, NO_WINDOW,
    CONNECTION_UNKNOWN, AUDIO_UNKNOWN, CALL_ACTIVE, NOT_CONFIRMED, PERMISSION,
    STATE_CONFLICT, NO_SESSION, START_FAILED, FOREGROUND_BLOCKED, FOREGROUND_UNKNOWN
}
