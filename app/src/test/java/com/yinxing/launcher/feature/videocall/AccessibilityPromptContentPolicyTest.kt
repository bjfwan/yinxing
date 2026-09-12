package com.yinxing.launcher.feature.videocall

import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.AccessibilityServiceReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityPromptContentPolicyTest {

    @Test
    fun disabledStateAsksUserToEnablePermission() {
        val content = requireNotNull(
            AccessibilityPromptContentPolicy.resolve(AccessibilityServiceReadiness.DISABLED)
        )

        assertEquals(R.string.accessibility_dialog_title, content.titleRes)
        assertEquals(R.string.accessibility_dialog_message, content.messageRes)
        assertEquals(R.string.action_go_to_settings, content.actionRes)
    }

    @Test
    fun enabledButDisconnectedStateNeverClaimsPermissionIsOff() {
        val content = requireNotNull(
            AccessibilityPromptContentPolicy.resolve(
                AccessibilityServiceReadiness.ENABLED_NOT_CONNECTED
            )
        )

        assertEquals(R.string.accessibility_connection_issue_title, content.titleRes)
        assertEquals(R.string.accessibility_connection_issue_message, content.messageRes)
        assertEquals(R.string.accessibility_check_settings, content.actionRes)
    }

    @Test
    fun connectedStateDoesNotShowAccessibilityPrompt() {
        assertNull(
            AccessibilityPromptContentPolicy.resolve(AccessibilityServiceReadiness.CONNECTED)
        )
    }
}
