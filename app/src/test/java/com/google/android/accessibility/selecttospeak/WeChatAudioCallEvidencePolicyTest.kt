package com.google.android.accessibility.selecttospeak

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatAudioCallEvidencePolicyTest {

    @Test
    fun normalToCommunicationTransitionIsEvidence() {
        assertTrue(
            WeChatAudioCallEvidencePolicy.hasCommunicationStarted(
                baselineMode = AudioManager.MODE_NORMAL,
                currentMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
    }

    @Test
    fun missingBaselineCannotConfirmCall() {
        assertFalse(
            WeChatAudioCallEvidencePolicy.hasCommunicationStarted(
                baselineMode = null,
                currentMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
    }

    @Test
    fun alreadyActiveCommunicationCannotConfirmNewCall() {
        assertFalse(
            WeChatAudioCallEvidencePolicy.hasCommunicationStarted(
                baselineMode = AudioManager.MODE_IN_COMMUNICATION,
                currentMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
    }

    @Test
    fun cellularCallTransitionCannotBeUsedAsWechatEvidence() {
        assertFalse(
            WeChatAudioCallEvidencePolicy.hasCommunicationStarted(
                baselineMode = AudioManager.MODE_IN_CALL,
                currentMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
    }
}
