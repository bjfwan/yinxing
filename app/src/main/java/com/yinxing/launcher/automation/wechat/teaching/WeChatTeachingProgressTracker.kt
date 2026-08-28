package com.yinxing.launcher.automation.wechat.teaching

import com.yinxing.launcher.automation.wechat.WeChatClassNames

enum class WeChatTeachingProgress {
    WECHAT_OPENED,
    CONTACT_OPENED,
    VIDEO_OPENED,
    CALL_STARTED
}

object WeChatTeachingProgressTracker {
    private const val WECHAT_VIDEO_DIALOG_PREFIX = "com.tencent.mm.ui.widget.dialog."

    fun latest(
        observations: List<WeChatTeachingObservation>,
        videoCallConfirmed: Boolean = false
    ): WeChatTeachingProgress {
        if (videoCallConfirmed) {
            return WeChatTeachingProgress.CALL_STARTED
        }

        val contactOpened = observations.any(::isContactOpened)
        if (observations.any(::isVideoClick) || (contactOpened && observations.any(::isVideoDialog))) {
            return WeChatTeachingProgress.VIDEO_OPENED
        }

        return if (contactOpened) {
            WeChatTeachingProgress.CONTACT_OPENED
        } else {
            WeChatTeachingProgress.WECHAT_OPENED
        }
    }

    private fun isContactOpened(observation: WeChatTeachingObservation): Boolean =
        observation.kind == WeChatTeachingObservationKind.WINDOW &&
            observation.windowClass in setOf(
                WeChatClassNames.CHATTING_UI,
                WeChatClassNames.CONTACT_INFO
            )

    private fun isVideoClick(observation: WeChatTeachingObservation): Boolean =
        observation.kind == WeChatTeachingObservationKind.CLICK &&
            observation.selector?.semanticLabel == WeChatTeachingSemanticLabel.VIDEO_CALL

    private fun isVideoDialog(observation: WeChatTeachingObservation): Boolean =
        observation.kind == WeChatTeachingObservationKind.WINDOW &&
            observation.windowClass.orEmpty().startsWith(WECHAT_VIDEO_DIALOG_PREFIX)
}
