package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingGuardReadinessEvaluatorTest {

    @Test
    fun phonePermissionIsTheFirstBlocker() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = false,
            hasNotificationPermission = true,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = true,
            backgroundStartConfirmed = true
        )

        assertEquals(IncomingGuardItem.PhonePermission, readiness.blocker?.item)
        assertEquals(5, readiness.completedCount)
    }

    @Test
    fun autoStartBlocksAfterDetectableItemsAreReady() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = false,
            backgroundStartConfirmed = true
        )

        assertEquals(IncomingGuardItem.AutoStart, readiness.blocker?.item)
        assertEquals(5, readiness.completedCount)
        assertTrue(readiness.items.first { it.item == IncomingGuardItem.AutoStart }.requiresManualConfirmation)
    }

    @Test
    fun allItemsReadyProducesReadyState() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = true,
            backgroundStartConfirmed = true
        )

        assertTrue(readiness.isReady)
        assertEquals(6, readiness.completedCount)
        assertNull(readiness.blocker)
    }

    @Test
    fun notificationPermissionIsBlockerWhenPhonePermissionGranted() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = false,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = true,
            backgroundStartConfirmed = true
        )

        assertEquals(IncomingGuardItem.NotificationPermission, readiness.blocker?.item)
        assertEquals(5, readiness.completedCount)
    }

    @Test
    fun defaultLauncherIsBlockerWhenPermissionsGranted() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = false,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = true,
            backgroundStartConfirmed = true
        )

        assertEquals(IncomingGuardItem.DefaultLauncher, readiness.blocker?.item)
    }

    @Test
    fun batteryOptimizationIsBlockerWhenEarlierItemsReady() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = false,
            autoStartConfirmed = true,
            backgroundStartConfirmed = true
        )

        assertEquals(IncomingGuardItem.BatteryOptimization, readiness.blocker?.item)
    }

    @Test
    fun backgroundStartIsLastBlocker() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = true,
            ignoresBatteryOptimizations = true,
            autoStartConfirmed = true,
            backgroundStartConfirmed = false
        )

        assertEquals(IncomingGuardItem.BackgroundStart, readiness.blocker?.item)
        assertTrue(readiness.items.first { it.item == IncomingGuardItem.BackgroundStart }.requiresManualConfirmation)
    }

    @Test
    fun noItemsReadyProducesZeroCompletedCount() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = false,
            hasNotificationPermission = false,
            isDefaultLauncher = false,
            ignoresBatteryOptimizations = false,
            autoStartConfirmed = false,
            backgroundStartConfirmed = false
        )

        assertEquals(0, readiness.completedCount)
        assertFalse(readiness.isReady)
        assertEquals(IncomingGuardItem.PhonePermission, readiness.blocker?.item)
    }

    @Test
    fun blockerReturnsFirstUnreadyItem() {
        val readiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = true,
            hasNotificationPermission = true,
            isDefaultLauncher = false,
            ignoresBatteryOptimizations = false,
            autoStartConfirmed = false,
            backgroundStartConfirmed = false
        )

        assertEquals(IncomingGuardItem.DefaultLauncher, readiness.blocker?.item)
    }
}
