package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatTeachingCallModeDetectorTest {

    @Test
    fun earpieceRouteConfirmsVoiceInsteadOfVideo() {
        assertEquals(
            WeChatTeachingCallMode.VOICE,
            WeChatTeachingCallModeDetector.detect(
                snapshot = null,
                selectedLabel = null,
                audioRoute = WeChatTeachingAudioRoute.EARPIECE
            )
        )
    }

    @Test
    fun speakerRouteConfirmsVideoOnCallEntry() {
        assertEquals(
            WeChatTeachingCallMode.VIDEO,
            WeChatTeachingCallModeDetector.detect(
                snapshot = null,
                selectedLabel = null,
                audioRoute = WeChatTeachingAudioRoute.SPEAKER
            )
        )
    }

    @Test
    fun explicitVoiceSelectionOverridesSpeakerRoute() {
        assertEquals(
            WeChatTeachingCallMode.VOICE,
            WeChatTeachingCallModeDetector.detect(
                snapshot = null,
                selectedLabel = WeChatTeachingSemanticLabel.VOICE_CALL,
                audioRoute = WeChatTeachingAudioRoute.SPEAKER
            )
        )
    }

    @Test
    fun cameraControlConfirmsVideoWithoutDependingOnAudioRoute() {
        assertEquals(
            WeChatTeachingCallMode.VIDEO,
            WeChatTeachingCallModeDetector.detect(
                snapshot = node(children = listOf(node(text = "切换摄像头"))),
                selectedLabel = null,
                audioRoute = WeChatTeachingAudioRoute.UNKNOWN
            )
        )
    }

    @Test
    fun confirmedVideoWithoutCompleteSelectorsIsAcceptedButNotSaved() {
        assertEquals(
            WeChatTeachingFinishDecision.ACCEPT_WITHOUT_RULE,
            WeChatTeachingFinishPolicy.decide(
                callMode = WeChatTeachingCallMode.VIDEO,
                learnedRuleCount = 0
            )
        )
    }

    @Test
    fun voiceCallStillFailsVideoTeaching() {
        assertEquals(
            WeChatTeachingFinishDecision.FAIL,
            WeChatTeachingFinishPolicy.decide(
                callMode = WeChatTeachingCallMode.VOICE,
                learnedRuleCount = 0
            )
        )
    }

    @Test
    fun confirmedVideoSavesEvenOneLearnedRule() {
        assertEquals(
            WeChatTeachingFinishDecision.SAVE_RULE,
            WeChatTeachingFinishPolicy.decide(
                callMode = WeChatTeachingCallMode.VIDEO,
                learnedRuleCount = 1
            )
        )
    }

    private fun node(
        text: String? = null,
        children: List<WeChatUiSnapshot> = emptyList()
    ) = WeChatUiSnapshot(text = text, children = children)
}
