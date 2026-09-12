package com.yinxing.launcher.common.util

enum class AccessibilityServiceReadiness {
    DISABLED,
    ENABLED_NOT_CONNECTED,
    CONNECTED
}

object AccessibilityServiceReadinessPolicy {
    fun resolve(
        settingEnabled: Boolean,
        serviceConnected: Boolean
    ): AccessibilityServiceReadiness {
        if (!settingEnabled) return AccessibilityServiceReadiness.DISABLED
        return if (serviceConnected) {
            AccessibilityServiceReadiness.CONNECTED
        } else {
            AccessibilityServiceReadiness.ENABLED_NOT_CONNECTED
        }
    }
}
