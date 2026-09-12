package com.yinxing.launcher.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityServiceReadinessPolicyTest {

    @Test
    fun disabledSettingIsReportedAsDisabled() {
        assertEquals(
            AccessibilityServiceReadiness.DISABLED,
            AccessibilityServiceReadinessPolicy.resolve(
                settingEnabled = false,
                serviceConnected = false
            )
        )
    }

    @Test
    fun enabledSettingWithoutLiveConnectionIsNotReportedAsDisabled() {
        assertEquals(
            AccessibilityServiceReadiness.ENABLED_NOT_CONNECTED,
            AccessibilityServiceReadinessPolicy.resolve(
                settingEnabled = true,
                serviceConnected = false
            )
        )
    }

    @Test
    fun enabledSettingWithLiveConnectionIsReady() {
        assertEquals(
            AccessibilityServiceReadiness.CONNECTED,
            AccessibilityServiceReadinessPolicy.resolve(
                settingEnabled = true,
                serviceConnected = true
            )
        )
    }

    @Test
    fun staleLiveReferenceCannotOverrideRevokedSetting() {
        assertEquals(
            AccessibilityServiceReadiness.DISABLED,
            AccessibilityServiceReadinessPolicy.resolve(
                settingEnabled = false,
                serviceConnected = true
            )
        )
    }
}
