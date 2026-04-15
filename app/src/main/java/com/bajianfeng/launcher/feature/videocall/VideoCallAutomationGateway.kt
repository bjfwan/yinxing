package com.bajianfeng.launcher.feature.videocall

fun interface VideoCallStateListener {
    fun onUpdate(state: VideoCallStateUpdate)
}

data class VideoCallStateUpdate(
    val message: String,
    val success: Boolean,
    val terminal: Boolean
)

interface VideoCallAutomationGateway {
    fun requestVideoCall(contactName: String, listener: VideoCallStateListener): String

    fun clearRequestListener(requestId: String)
}
