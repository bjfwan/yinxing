package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel

internal enum class WeChatTeachingCallMode {
    UNKNOWN,
    VOICE,
    VIDEO
}

internal enum class WeChatTeachingAudioRoute {
    UNKNOWN,
    EARPIECE,
    SPEAKER,
    OTHER
}

internal object WeChatTeachingCallModeDetector {
    private val videoTexts = listOf(
        "切换摄像头",
        "翻转摄像头",
        "关闭摄像头",
        "打开摄像头",
        "切换到语音通话"
    )
    private val voiceTexts = listOf("切换到视频通话")

    fun detect(
        snapshot: WeChatUiSnapshot?,
        selectedLabel: WeChatTeachingSemanticLabel?,
        audioRoute: WeChatTeachingAudioRoute
    ): WeChatTeachingCallMode {
        when (selectedLabel) {
            WeChatTeachingSemanticLabel.VOICE_CALL -> return WeChatTeachingCallMode.VOICE
            WeChatTeachingSemanticLabel.VIDEO_CALL -> return WeChatTeachingCallMode.VIDEO
            else -> Unit
        }

        val texts = snapshot?.flatten()
            ?.flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            ?.mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            ?.toList()
            .orEmpty()
        if (texts.containsAny(videoTexts)) return WeChatTeachingCallMode.VIDEO
        if (texts.containsAny(voiceTexts)) return WeChatTeachingCallMode.VOICE

        return when (audioRoute) {
            WeChatTeachingAudioRoute.EARPIECE -> WeChatTeachingCallMode.VOICE
            WeChatTeachingAudioRoute.SPEAKER -> WeChatTeachingCallMode.VIDEO
            WeChatTeachingAudioRoute.UNKNOWN,
            WeChatTeachingAudioRoute.OTHER -> WeChatTeachingCallMode.UNKNOWN
        }
    }

    private fun List<String>.containsAny(expected: Collection<String>): Boolean =
        any { actual -> expected.any(actual::contains) }
}

internal enum class WeChatTeachingFinishDecision {
    SAVE_RULE,
    ACCEPT_WITHOUT_RULE,
    FAIL
}

internal object WeChatTeachingFinishPolicy {
    fun decide(
        callMode: WeChatTeachingCallMode,
        learnedRuleCount: Int
    ): WeChatTeachingFinishDecision = when {
        callMode != WeChatTeachingCallMode.VIDEO -> WeChatTeachingFinishDecision.FAIL
        learnedRuleCount > 0 -> WeChatTeachingFinishDecision.SAVE_RULE
        else -> WeChatTeachingFinishDecision.ACCEPT_WITHOUT_RULE
    }
}
