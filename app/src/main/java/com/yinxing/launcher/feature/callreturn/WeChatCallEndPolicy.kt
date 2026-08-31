package com.yinxing.launcher.feature.callreturn

import android.media.AudioManager

internal object WeChatCallWindowTracker {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private val meaningfulClasses = setOf(
        "com.tencent.mm.plugin.voip.ui.VideoActivity",
        "com.tencent.mm.ui.LauncherUI",
        "com.tencent.mm.ui.chatting.ChattingUI",
        "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
    )

    fun remember(
        previousClass: String?,
        packageName: String?,
        observedClass: String?
    ): String? = if (packageName == WECHAT_PACKAGE && observedClass in meaningfulClasses) {
        observedClass
    } else {
        previousClass
    }
}

internal object WeChatCallEndPolicy {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private val postCallClasses = setOf(
        "com.tencent.mm.ui.LauncherUI",
        "com.tencent.mm.ui.chatting.ChattingUI",
        "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
    )

    fun shouldComplete(
        packageName: String?,
        className: String?,
        videoActivityVisible: Boolean,
        audioMode: Int
    ): Boolean = packageName == WECHAT_PACKAGE &&
        className in postCallClasses &&
        !videoActivityVisible &&
        audioMode != AudioManager.MODE_IN_CALL &&
        audioMode != AudioManager.MODE_IN_COMMUNICATION
}
