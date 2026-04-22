package com.bajianfeng.launcher.feature.videocall

import com.google.android.accessibility.selecttospeak.SelectToSpeakService

object SelectToSpeakAutomationGateway : VideoCallAutomationGateway {
    override fun requestVideoCall(contactName: String, listener: VideoCallStateListener): String {
        return SelectToSpeakService.requestVideoCall(contactName) { update ->
            listener.onUpdate(
                VideoCallStateUpdate(
                    message = update.message,
                    success = update.success,
                    terminal = update.terminal,
                    step = update.step,
                    page = update.page
                )
            )
        }
    }

    override fun clearRequestListener(requestId: String) {
        SelectToSpeakService.clearRequestListener(requestId)
    }
}
