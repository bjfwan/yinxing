package com.yinxing.launcher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun defaultScreenIsStandardOverview() {
        assertEquals(SettingsScreen.StandardOverview, SettingsScreen.from(null, null))
    }

    @Test
    fun elderModeOpensElderOverview() {
        assertEquals(SettingsScreen.ElderOverview, SettingsScreen.from("elder", null))
    }

    @Test
    fun sectionTakesPriorityOverMode() {
        assertEquals(SettingsScreen.Contacts, SettingsScreen.from("elder", "contacts"))
        assertEquals(SettingsScreen.Calls, SettingsScreen.from(null, "calls"))
        assertEquals(SettingsScreen.Permissions, SettingsScreen.from(null, "permissions"))
        assertEquals(SettingsScreen.Device, SettingsScreen.from(null, "device"))
        assertEquals(SettingsScreen.System, SettingsScreen.from(null, "system"))
    }
}
