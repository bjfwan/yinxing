package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingGuardDiagnosticEvaluatorTest {

    @Test
    fun allReadyProducesNoFailedItems() {
        val snapshot = IncomingGuardDiagnosticEvaluator.evaluate(
            hasNotificationPermission = true,
            canDrawOverlays = true,
            backgroundStartConfirmed = true,
            hasAccessibilityService = true,
            ignoresBatteryOptimizations = true,
            foregroundServiceStarted = true
        )

        assertEquals(0, snapshot.failedItems.size)
        assertTrue(snapshot.displayText().contains("已完成"))
    }

    @Test
    fun allFailedProducesAllFailedItems() {
        val snapshot = IncomingGuardDiagnosticEvaluator.evaluate(
            hasNotificationPermission = false,
            canDrawOverlays = false,
            backgroundStartConfirmed = false,
            hasAccessibilityService = false,
            ignoresBatteryOptimizations = false,
            foregroundServiceStarted = false
        )

        assertEquals(6, snapshot.failedItems.size)
        assertTrue(snapshot.displayText().contains("待处理"))
    }

    @Test
    fun foregroundServiceNullShowsUndetected() {
        val snapshot = IncomingGuardDiagnosticEvaluator.evaluate(
            hasNotificationPermission = true,
            canDrawOverlays = true,
            backgroundStartConfirmed = true,
            hasAccessibilityService = true,
            ignoresBatteryOptimizations = true,
            foregroundServiceStarted = null
        )

        assertEquals(0, snapshot.failedItems.size)
        assertTrue(snapshot.displayText().contains("未检测"))
    }

    @Test
    fun mixedStatesProduceCorrectDisplayText() {
        val snapshot = IncomingGuardDiagnosticEvaluator.evaluate(
            hasNotificationPermission = true,
            canDrawOverlays = false,
            backgroundStartConfirmed = true,
            hasAccessibilityService = false,
            ignoresBatteryOptimizations = true,
            foregroundServiceStarted = null
        )

        assertEquals(2, snapshot.failedItems.size)
        val text = snapshot.displayText()
        assertTrue(text.contains("悬浮窗：待处理"))
        assertTrue(text.contains("无障碍：待处理"))
        assertTrue(text.contains("前台服务：未检测"))
    }

    @Test
    fun diagnosticItemLabels() {
        assertEquals("通知权限", IncomingGuardDiagnosticItem.NotificationPermission.label)
        assertEquals("悬浮窗", IncomingGuardDiagnosticItem.OverlayPermission.label)
        assertEquals("后台启动", IncomingGuardDiagnosticItem.BackgroundStart.label)
        assertEquals("无障碍", IncomingGuardDiagnosticItem.Accessibility.label)
        assertEquals("省电限制", IncomingGuardDiagnosticItem.BatteryOptimization.label)
        assertEquals("前台服务", IncomingGuardDiagnosticItem.ForegroundService.label)
    }
}
