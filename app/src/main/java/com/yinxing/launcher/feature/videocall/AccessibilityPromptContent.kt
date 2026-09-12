package com.yinxing.launcher.feature.videocall

import androidx.annotation.StringRes
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.AccessibilityServiceReadiness

internal data class AccessibilityPromptContent(
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val actionRes: Int
)

internal object AccessibilityPromptContentPolicy {
    fun resolve(readiness: AccessibilityServiceReadiness): AccessibilityPromptContent? {
        return when (readiness) {
            AccessibilityServiceReadiness.DISABLED -> AccessibilityPromptContent(
                titleRes = R.string.accessibility_dialog_title,
                messageRes = R.string.accessibility_dialog_message,
                actionRes = R.string.action_go_to_settings
            )
            AccessibilityServiceReadiness.ENABLED_NOT_CONNECTED -> AccessibilityPromptContent(
                titleRes = R.string.accessibility_connection_issue_title,
                messageRes = R.string.accessibility_connection_issue_message,
                actionRes = R.string.accessibility_check_settings
            )
            AccessibilityServiceReadiness.CONNECTED -> null
        }
    }
}
