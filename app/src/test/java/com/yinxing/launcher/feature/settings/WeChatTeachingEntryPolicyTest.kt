package com.yinxing.launcher.feature.settings

import com.google.android.accessibility.selecttospeak.WeChatTeachingPrepareResult
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingEntryPolicyTest {

    @Test
    fun disabledServiceOpensAccessibilitySettings() {
        val decision = WeChatTeachingEntryPolicy.resolve(
            accessibilityEnabled = false,
            prepareResult = WeChatTeachingPrepareResult.SERVICE_NOT_CONNECTED
        )

        assertEquals(R.string.settings_wechat_teaching_service_off, decision.messageRes)
        assertTrue(decision.openAccessibilitySettings)
    }

    @Test
    fun enabledButNotConnectedDoesNotClaimServiceIsOff() {
        val decision = WeChatTeachingEntryPolicy.resolve(
            accessibilityEnabled = true,
            prepareResult = WeChatTeachingPrepareResult.SERVICE_NOT_CONNECTED
        )

        assertEquals(R.string.settings_wechat_teaching_service_connecting, decision.messageRes)
        assertFalse(decision.openAccessibilitySettings)
    }

    @Test
    fun overlayFailureIsReportedSeparately() {
        val decision = WeChatTeachingEntryPolicy.resolve(
            accessibilityEnabled = true,
            prepareResult = WeChatTeachingPrepareResult.OVERLAY_UNAVAILABLE
        )

        assertEquals(R.string.settings_wechat_teaching_overlay_failed, decision.messageRes)
        assertFalse(decision.openAccessibilitySettings)
    }
}
