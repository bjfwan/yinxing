package com.yinxing.launcher.feature.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilySetupReadinessTest {
    @Test
    fun twoRequiredItemsLetFamilyFinishWhenLauncherIsReady() {
        val readiness = familySetupReadiness(1, phonePermissionGranted = true, defaultLauncher = true)

        assertEquals(2, readiness.completedCount)
        assertTrue(readiness.canFinish)
    }

    @Test
    fun missingContactKeepsSetupIncomplete() {
        val readiness = familySetupReadiness(0, phonePermissionGranted = true, defaultLauncher = true)

        assertEquals(1, readiness.completedCount)
        assertFalse(readiness.canFinish)
    }

    @Test
    fun missingPermissionKeepsSetupIncomplete() {
        val readiness = familySetupReadiness(1, phonePermissionGranted = false, defaultLauncher = true)

        assertEquals(1, readiness.completedCount)
        assertFalse(readiness.canFinish)
    }

    @Test
    fun missingDefaultLauncherDoesNotBlockEnteringHome() {
        val readiness = familySetupReadiness(1, phonePermissionGranted = true, defaultLauncher = false)

        assertEquals(2, readiness.completedCount)
        assertTrue(readiness.canFinish)
    }
}
