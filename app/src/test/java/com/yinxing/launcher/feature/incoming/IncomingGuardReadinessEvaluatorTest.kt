package com.bajianfeng.launcher.feature.incoming

import org.junit.Assert.assertEquals
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
}
