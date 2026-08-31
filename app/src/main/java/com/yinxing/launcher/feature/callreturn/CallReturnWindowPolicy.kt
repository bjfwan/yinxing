package com.yinxing.launcher.feature.callreturn

internal enum class CallReturnWindowAction {
    IGNORE,
    CHECK_WECHAT_ENDED,
    USER_ESCAPED
}

internal object CallReturnWindowPolicy {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val WECHAT_VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"
    private val wechatPostCallClasses = setOf(
        "com.tencent.mm.ui.LauncherUI",
        "com.tencent.mm.ui.chatting.ChattingUI",
        "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
    )

    fun decide(
        origin: CallReturnOrigin,
        appPackage: String,
        packageName: String?,
        className: String?
    ): CallReturnWindowAction {
        val pkg = packageName.orEmpty()
        if (pkg.isBlank() || pkg == appPackage || pkg == "com.android.systemui") {
            return CallReturnWindowAction.IGNORE
        }
        return when (origin) {
            CallReturnOrigin.WECHAT_VIDEO -> when {
                pkg == WECHAT_PACKAGE && className == WECHAT_VIDEO_ACTIVITY ->
                    CallReturnWindowAction.IGNORE
                pkg == WECHAT_PACKAGE && className in wechatPostCallClasses ->
                    CallReturnWindowAction.CHECK_WECHAT_ENDED
                pkg == WECHAT_PACKAGE -> CallReturnWindowAction.IGNORE
                else -> CallReturnWindowAction.USER_ESCAPED
            }
            CallReturnOrigin.SYSTEM_PHONE -> if (isSystemCallSurface(pkg)) {
                CallReturnWindowAction.IGNORE
            } else {
                CallReturnWindowAction.USER_ESCAPED
            }
        }
    }

    private fun isSystemCallSurface(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized == "com.android.phone" ||
            normalized.contains("incall") ||
            normalized.contains("dialer") ||
            normalized.contains("telecom")
    }
}
