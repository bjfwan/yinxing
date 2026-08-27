package com.yinxing.launcher.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemSettingsCatalogTest {
    @Test
    fun vivoUsesVerifiedBackgroundStartupManagerFirst() {
        val target = OemSettingsCatalog.targets(
            manufacturer = "vivo",
            capability = OemSettingsCapability.BACKGROUND_START
        ).first()

        assertEquals("com.vivo.permissionmanager", target.packageName)
        assertEquals(
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            target.className
        )
        assertEquals("packagename", target.packageExtraKey)
    }

    @Test
    fun xiaomiBackgroundPermissionUsesDocumentedPermissionEditorAction() {
        val target = OemSettingsCatalog.targets(
            manufacturer = "Xiaomi",
            capability = OemSettingsCapability.BACKGROUND_START
        ).first()

        assertEquals("miui.intent.action.APP_PERM_EDITOR", target.action)
        assertEquals("extra_pkgname", target.packageExtraKey)
    }

    @Test
    fun relatedBrandsUseTheirSharedVendorSettings() {
        assertEquals(
            "com.vivo.permissionmanager",
            OemSettingsCatalog.targets("iQOO", OemSettingsCapability.AUTO_START).first().packageName
        )
        assertEquals(
            "com.oplus.safecenter",
            OemSettingsCatalog.targets("OnePlus", OemSettingsCapability.AUTO_START).first().packageName
        )
    }

    @Test
    fun unknownManufacturerDoesNotTryUnrelatedVendorActivities() {
        assertTrue(
            OemSettingsCatalog.targets("unknown", OemSettingsCapability.AUTO_START).isEmpty()
        )
    }
}
