package com.yinxing.launcher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun defaultScreenIsStandardOverview() {
        assertEquals(SettingsScreen.StandardOverview, SettingsScreen.from(null, null))
    }

    @Test
    fun legacyElderModeUsesUnifiedOverview() {
        assertEquals(SettingsScreen.StandardOverview, SettingsScreen.from("elder", null))
    }

    @Test
    fun sectionTakesPriorityOverMode() {
        assertEquals(SettingsScreen.Contacts, SettingsScreen.from("elder", "contacts"))
        assertEquals(SettingsScreen.Calls, SettingsScreen.from(null, "calls"))
        assertEquals("diagnostics", SettingsScreen.from(null, "diagnostics").key)
        assertEquals(SettingsScreen.Permissions, SettingsScreen.from(null, "permissions"))
        assertEquals("background", SettingsScreen.from(null, "background").key)
        assertEquals(SettingsScreen.Device, SettingsScreen.from(null, "device"))
        assertEquals("display", SettingsScreen.from(null, "display").key)
        assertEquals("weather", SettingsScreen.from(null, "weather").key)
        assertEquals(SettingsScreen.System, SettingsScreen.from(null, "system"))
        assertEquals("safety", SettingsScreen.from(null, "safety").key)
    }
}
