package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatPackage
import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatAutomationRecoveryPolicyTest {
    @Test
    fun `waits for a fresh snapshot after submitting search text`() {
        assertEquals(
            SearchInputDecision.WAIT_FOR_VERIFICATION,
            WeChatSearchInputPolicy.decide(submitted = true, verified = false, elapsedMs = 600, failedAttempts = 0)
        )
        assertEquals(
            SearchInputDecision.COMPLETE,
            WeChatSearchInputPolicy.decide(submitted = true, verified = true, elapsedMs = 600, failedAttempts = 0)
        )
    }

    @Test
    fun `retries search only after the verification window expires`() {
        assertEquals(
            SearchInputDecision.RETRY,
            WeChatSearchInputPolicy.decide(submitted = true, verified = false, elapsedMs = 1_600, failedAttempts = 2)
        )
        assertEquals(
            SearchInputDecision.FAIL,
            WeChatSearchInputPolicy.decide(submitted = true, verified = false, elapsedMs = 1_600, failedAttempts = 5)
        )
    }

    @Test
    fun `relaunches WeChat only after another app remains foreground`() {
        assertEquals(
            ForegroundRecoveryDecision.WAIT,
            WeChatForegroundRecoveryPolicy.decide(WeChatPackage.NAME, missingRootMs = 2_000, recoveryAttempts = 0)
        )
        assertEquals(
            ForegroundRecoveryDecision.WAIT,
            WeChatForegroundRecoveryPolicy.decide("com.oppo.launcher", missingRootMs = 800, recoveryAttempts = 0)
        )
        assertEquals(
            ForegroundRecoveryDecision.RELAUNCH,
            WeChatForegroundRecoveryPolicy.decide("com.oppo.launcher", missingRootMs = 2_000, recoveryAttempts = 0)
        )
        assertEquals(
            ForegroundRecoveryDecision.FAIL,
            WeChatForegroundRecoveryPolicy.decide("com.oppo.launcher", missingRootMs = 2_000, recoveryAttempts = 2)
        )
        assertEquals(
            ForegroundRecoveryDecision.WAIT,
            WeChatForegroundRecoveryPolicy.decide("com.baidu.input_oppo", missingRootMs = 5_000, recoveryAttempts = 0)
        )
    }
}
