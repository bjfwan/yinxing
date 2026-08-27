package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemIncomingCallPolicyTest {

    @Test
    fun xiaomiRequiresDefaultPhoneRoleForPhonePermissions() {
        val policy = OemIncomingCallPolicy.forManufacturer("Xiaomi")

        assertTrue(policy.requiresDefaultPhoneRole)
        assertTrue(policy.requirements.contains(OemIncomingRequirement.AutoStart))
        assertTrue(policy.requirements.contains(OemIncomingRequirement.BackgroundPopup))
    }

    @Test
    fun vivoFlagsSideLoadRiskAndBackgroundRequirements() {
        val policy = OemIncomingCallPolicy.forManufacturer("iQOO")

        assertTrue(policy.requirements.contains(OemIncomingRequirement.SideLoadRiskControl))
        assertTrue(policy.requirements.contains(OemIncomingRequirement.AutoStart))
        assertTrue(policy.requirements.contains(OemIncomingRequirement.BatteryUnrestricted))
    }

    @Test
    fun samsungUsesNeverSleepRequirementWithoutVendorPhonePermissionRule() {
        val policy = OemIncomingCallPolicy.forManufacturer("samsung")

        assertFalse(policy.requiresDefaultPhoneRole)
        assertEquals(
            setOf(OemIncomingRequirement.NeverSleep, OemIncomingRequirement.SideLoadRiskControl),
            policy.requirements
        )
    }

    @Test
    fun unknownVendorKeepsOnlyAndroidStandardRequirements() {
        val policy = OemIncomingCallPolicy.forManufacturer("unknown")

        assertFalse(policy.requiresDefaultPhoneRole)
        assertTrue(policy.requirements.isEmpty())
    }
}
