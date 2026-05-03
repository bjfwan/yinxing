package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingCallFailureCategoryTest {

    @Test
    fun fromCodeReturnsMatchingCategory() {
        assertEquals(IncomingCallFailureCategory.PhonePermission, IncomingCallFailureCategory.fromCode("phone_permission"))
        assertEquals(IncomingCallFailureCategory.NotificationPermission, IncomingCallFailureCategory.fromCode("notification_permission"))
        assertEquals(IncomingCallFailureCategory.Overlay, IncomingCallFailureCategory.fromCode("overlay"))
        assertEquals(IncomingCallFailureCategory.BackgroundStart, IncomingCallFailureCategory.fromCode("background_start"))
        assertEquals(IncomingCallFailureCategory.Accessibility, IncomingCallFailureCategory.fromCode("accessibility"))
        assertEquals(IncomingCallFailureCategory.BatteryOptimization, IncomingCallFailureCategory.fromCode("battery_optimization"))
        assertEquals(IncomingCallFailureCategory.ForegroundService, IncomingCallFailureCategory.fromCode("foreground_service"))
        assertEquals(IncomingCallFailureCategory.CallAction, IncomingCallFailureCategory.fromCode("call_action"))
        assertEquals(IncomingCallFailureCategory.Broadcast, IncomingCallFailureCategory.fromCode("broadcast"))
        assertEquals(IncomingCallFailureCategory.UnsupportedPlatform, IncomingCallFailureCategory.fromCode("unsupported_platform"))
        assertEquals(IncomingCallFailureCategory.Unknown, IncomingCallFailureCategory.fromCode("unknown"))
    }

    @Test
    fun fromCodeReturnsNullForInvalidCode() {
        assertNull(IncomingCallFailureCategory.fromCode("nonexistent"))
        assertNull(IncomingCallFailureCategory.fromCode(null))
        assertNull(IncomingCallFailureCategory.fromCode(""))
    }

    @Test
    fun eachCategoryHasNonEmptyCodeAndLabel() {
        IncomingCallFailureCategory.entries.forEach { category ->
            assert(category.code.isNotEmpty()) { "${category.name} has empty code" }
            assert(category.label.isNotEmpty()) { "${category.name} has empty label" }
        }
    }
}

class IncomingCallFailureReasonTest {

    @Test
    fun displayTextWithoutDetailShowsCategoryLabel() {
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.PhonePermission)
        assertEquals("电话权限", reason.displayText())
    }

    @Test
    fun displayTextWithDetailShowsCategoryAndDetail() {
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.Overlay, "无悬浮窗权限")
        assertEquals("悬浮窗：无悬浮窗权限", reason.displayText())
    }

    @Test
    fun displayTextWithBlankDetailShowsCategoryLabelOnly() {
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.Broadcast, "   ")
        assertEquals("来电广播", reason.displayText())
    }

    @Test
    fun displayTextWithEmptyDetailShowsCategoryLabelOnly() {
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.CallAction, "")
        assertEquals("接听指令", reason.displayText())
    }
}
