package com.google.android.accessibility.selecttospeak

/**
 * 识别微信的准入门槛页面：未登录的登录页、首启/更新后的隐私协议页。
 *
 * 这些页面会让自动化步骤一直轮询到超时——提前识别后直接给出明确失败原因。
 */
internal object WeChatLoginPageDetector {

    enum class Gate { NONE, LOGIN, AGREEMENT }

    private val loginPrimarySignals = listOf("登录", "注册")
    private val loginContextSignals = listOf(
        "手机号登录",
        "请填写手机号",
        "微信号",
        "QQ号",
        "邮箱登录",
        "找回密码",
        "使用其他方式登录",
        "登录其他账号"
    )
    private val agreementSignals = listOf(
        "隐私保护指引",
        "用户服务协议",
        "我已阅读并同意",
        "软件许可及服务协议"
    )

    fun detect(snapshot: WeChatUiSnapshot?, currentClass: String? = null): Gate {
        if (isLoginClassName(currentClass)) return Gate.LOGIN
        val texts = snapshot?.flatten()
            ?.flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            ?.mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            ?.toList()
            .orEmpty()
        if (texts.isEmpty()) return Gate.NONE

        val hasLoginPrimary = texts.any { t -> loginPrimarySignals.any { p -> t.contains(p) } }
        val hasLoginContext = loginContextSignals.any { c -> texts.any { t -> t.contains(c) } }
        if (hasLoginPrimary && hasLoginContext) return Gate.LOGIN

        val agreementHits = agreementSignals.count { s -> texts.any { t -> t.contains(s) } }
        if (agreementHits >= 2) return Gate.AGREEMENT
        return Gate.NONE
    }

    /**
     * 微信登录/注册流程页都在 plugin.account 包下（LoginPasswordUI、LoginUI、
     * MobileInputUI、RegByMobileRegAIOUI 等）。无障碍树为空时文本信号全丢，
     * 只能靠窗口类名兜底识别。
     */
    fun isLoginClassName(className: String?): Boolean {
        return className?.startsWith(LOGIN_CLASS_PREFIX) == true
    }

    private const val LOGIN_CLASS_PREFIX = "com.tencent.mm.plugin.account."
}
