package com.google.android.accessibility.selecttospeak

internal enum class WeChatCallStartStatus {
    PENDING,
    CONFIRMED,
    BLOCKED
}

internal data class WeChatCallStartAssessment(
    val status: WeChatCallStartStatus,
    val reasons: List<String>,
    val userMessage: String? = null
)

internal object WeChatCallStartVerifier {
    private val outgoingCallTexts = listOf(
        "正在等待对方接受邀请",
        "等待对方接受邀请",
        "正在等待对方接听",
        "等待对方接听",
        "正在呼叫",
        "呼叫中"
    )
    private val callControlTexts = listOf(
        "静音",
        "挂断",
        "切换摄像头",
        "翻转摄像头",
        "关闭摄像头",
        "打开摄像头",
        "扬声器",
        "免提"
    )
    private val permissionBlockTexts = listOf(
        "无法使用相机",
        "无法使用摄像头",
        "无法使用麦克风",
        "相机权限",
        "摄像头权限",
        "麦克风权限"
    )
    private val networkBlockTexts = listOf(
        "网络连接失败",
        "网络不可用",
        "当前网络不可用",
        "网络异常"
    )
    private val genericBlockTexts = listOf(
        "无法发起视频通话",
        "视频通话失败",
        "操作过于频繁",
        "暂时无法呼叫"
    )

    fun assess(snapshot: WeChatUiSnapshot?, className: String?): WeChatCallStartAssessment {
        val texts = snapshot?.flatten()
            ?.flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            ?.mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            ?.toList()
            .orEmpty()

        if (texts.containsAny(permissionBlockTexts)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.BLOCKED,
                reasons = listOf("permission_block"),
                userMessage = "微信缺少视频通话权限"
            )
        }
        if (texts.containsAny(networkBlockTexts)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.BLOCKED,
                reasons = listOf("network_block"),
                userMessage = "微信视频网络连接失败"
            )
        }
        if (texts.containsAny(genericBlockTexts)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.BLOCKED,
                reasons = listOf("wechat_block"),
                userMessage = "微信未能发起视频通话"
            )
        }

        if (snapshot != null && WeChatUiSnapshotAnalyzer.isVideoCallSheetVisible(snapshot)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.PENDING,
                reasons = listOf("video_sheet")
            )
        }

        if (isVoipClass(className)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.CONFIRMED,
                reasons = listOf("voip_window")
            )
        }

        if (texts.containsAny(outgoingCallTexts)) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.CONFIRMED,
                reasons = listOf("outgoing_call_text")
            )
        }
        val controlCount = callControlTexts.count { expected ->
            texts.any { actual -> actual == expected || actual.contains(expected) }
        }
        if (controlCount >= 2) {
            return WeChatCallStartAssessment(
                status = WeChatCallStartStatus.CONFIRMED,
                reasons = listOf("call_controls")
            )
        }

        return WeChatCallStartAssessment(
            status = WeChatCallStartStatus.PENDING,
            reasons = listOf("missing_call_evidence")
        )
    }

    private fun isVoipClass(className: String?): Boolean {
        val normalized = className?.trim()?.lowercase().orEmpty()
        return normalized.startsWith("com.tencent.mm.") &&
            (normalized.contains(".plugin.voip.") || normalized.contains(".voip.ui."))
    }

    private fun List<String>.containsAny(expectedTexts: Collection<String>): Boolean {
        return any { actual -> expectedTexts.any { expected -> actual.contains(expected) } }
    }
}

internal data class WeChatCallVerificationState(
    val consecutiveConfirmations: Int = 0
)

internal enum class WeChatCallVerificationAction {
    WAIT,
    COMPLETE,
    FAIL
}

internal data class WeChatCallVerificationDecision(
    val nextState: WeChatCallVerificationState,
    val action: WeChatCallVerificationAction
)

internal object WeChatCallVerificationPolicy {
    private const val REQUIRED_CONFIRMATIONS = 2

    fun decide(
        state: WeChatCallVerificationState,
        assessment: WeChatCallStartAssessment
    ): WeChatCallVerificationDecision {
        return when (assessment.status) {
            WeChatCallStartStatus.BLOCKED -> WeChatCallVerificationDecision(
                nextState = WeChatCallVerificationState(),
                action = WeChatCallVerificationAction.FAIL
            )
            WeChatCallStartStatus.PENDING -> WeChatCallVerificationDecision(
                nextState = WeChatCallVerificationState(),
                action = WeChatCallVerificationAction.WAIT
            )
            WeChatCallStartStatus.CONFIRMED -> {
                val nextState = state.copy(
                    consecutiveConfirmations = state.consecutiveConfirmations + 1
                )
                WeChatCallVerificationDecision(
                    nextState = nextState,
                    action = if (nextState.consecutiveConfirmations >= REQUIRED_CONFIRMATIONS) {
                        WeChatCallVerificationAction.COMPLETE
                    } else {
                        WeChatCallVerificationAction.WAIT
                    }
                )
            }
        }
    }
}
