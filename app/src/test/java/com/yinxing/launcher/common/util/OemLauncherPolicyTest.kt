package com.yinxing.launcher.common.util

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemLauncherPolicyTest {
    @Test
    fun vivoRequiresTheAdditionalDesktopReplacementGate() {
        val profile = OemLauncherPolicy.profile("vivo")

        assertEquals(OemLauncherSupport.EXTRA_SECURITY_GATE, profile.support)
        assertEquals("vivo", profile.vendorKey)
        assertEquals("com.bbk.launcher2", profile.iconPackages.first())
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, profile.secondarySettingsAction)
    }

    @Test
    fun xiaomiChinaFirmwareIsPresentedAsRestricted() {
        val profile = OemLauncherPolicy.profile("Xiaomi")

        assertEquals(OemLauncherSupport.RESTRICTED, profile.support)
        assertEquals("com.miui.home", profile.iconPackages.first())
        assertEquals(null, profile.secondarySettingsAction)
    }

    @Test
    fun relatedBrandsShareTheirVendorGuidanceAndOfficialIconSources() {
        assertEquals("vivo", OemLauncherPolicy.profile("iQOO").vendorKey)
        assertEquals("oplus", OemLauncherPolicy.profile("realme").vendorKey)
        assertEquals("oplus", OemLauncherPolicy.profile("OnePlus").vendorKey)
        assertEquals("honor", OemLauncherPolicy.profile("HONOR").vendorKey)
    }

    @Test
    fun unknownDevicesUseTheStandardAndroidFlowAndSettingsIcon() {
        val profile = OemLauncherPolicy.profile("unknown")

        assertEquals(OemLauncherSupport.STANDARD, profile.support)
        assertTrue(profile.iconPackages.contains("com.android.settings"))
    }
}
