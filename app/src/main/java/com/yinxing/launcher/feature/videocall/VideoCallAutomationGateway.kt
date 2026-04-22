package com.bajianfeng.launcher.feature.videocall

import com.bajianfeng.launcher.automation.wechat.model.AutomationState

fun interface VideoCallStateListener {
    fun onUpdate(state: VideoCallStateUpdate)
}

data class VideoCallStateUpdate(
    val message: String,
    val success: Boolean,
    val terminal: Boolean,
    val step: AutomationState = AutomationState.IDLE,
    val page: String? = null
)

interface VideoCallAutomationGateway {
    fun requestVideoCall(contactName: String, listener: VideoCallStateListener): String

    fun clearRequestListener(requestId: String)
}
