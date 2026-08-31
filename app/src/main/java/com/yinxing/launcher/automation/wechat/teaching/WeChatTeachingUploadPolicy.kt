package com.yinxing.launcher.automation.wechat.teaching

/** Historical field retained only for LogAnalyse payload compatibility. */
enum class WeChatTeachingReplayResult {
    NOT_RUN,
    CANCELLED,
    VIDEO_CALL_CONFIRMED,
    VOICE_CALL,
    TIMEOUT,
    STEP_FAILED
}

data class WeChatTeachingUploadChoice(val uploadAnonymousData: Boolean)

object WeChatTeachingUploadChoicePolicy {
    fun newSession(): WeChatTeachingUploadChoice = WeChatTeachingUploadChoice(
        uploadAnonymousData = true
    )

    fun shouldSaveLocally(
        @Suppress("UNUSED_PARAMETER") uploadAnonymousData: Boolean,
        @Suppress("UNUSED_PARAMETER") uploadSucceeded: Boolean
    ): Boolean = true

    fun shouldUpload(uploadAnonymousData: Boolean): Boolean = uploadAnonymousData
}
