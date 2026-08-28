package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobsterSettingEventFactoryTest {
    @Test
    fun `uses fixed operation codes for screens and toggles`() {
        val screen = LobsterSettingEventFactory.screenOpened("Permissions")
        val enabled = LobsterSettingEventFactory.toggleChanged(LobsterSetting.AUTO_ANSWER, true)

        assertEquals(LobsterLogCategory.SETTINGS, screen.category)
        assertEquals("open_settings_permissions", screen.action)
        assertEquals(LobsterEventType.OPERATION, enabled.eventType)
        assertEquals("change_auto_answer", enabled.action)
        assertEquals("自动接听已开启", enabled.summary)
        assertEquals("open_settings_safety", LobsterSettingEventFactory.screenOpened("Safety").action)
    }

    @Test
    fun `unknown screen names do not become action values`() {
        val event = LobsterSettingEventFactory.screenOpened("联系人张三")

        assertEquals("open_settings", event.action)
        assertFalse(event.logLine.contains("张三"))
    }

    @Test
    fun `fine operations use fixed non sensitive actions`() {
        val contact = LobsterSettingEventFactory.contactChanged(
            LobsterContactChannel.PHONE,
            LobsterContactChange.ADDED
        )
        val permission = LobsterSettingEventFactory.permissionResult(
            LobsterPermissionTarget.LOCATION,
            granted = false
        )
        val requested = LobsterSettingEventFactory.permissionRequested(LobsterPermissionTarget.LOCATION)
        val contactsPermission = LobsterSettingEventFactory.permissionResult(
            LobsterPermissionTarget.CONTACTS,
            granted = true
        )
        val contactFailure = LobsterSettingEventFactory.contactChangeFailed(
            LobsterContactChannel.PHONE,
            LobsterContactChange.UPDATED
        )

        assertEquals("add_phone_contact", contact.action)
        assertFalse(contact.logLine.contains("张三"))
        assertEquals("request_location_permission", permission.action)
        assertEquals(LobsterReportStatus.REPORTED, requested.status)
        assertEquals("request_contacts_permission", contactsPermission.action)
        assertEquals(LobsterReportStatus.ERROR, permission.status)
        assertEquals("PERMISSION_DENIED", permission.details.errorCode)
        assertEquals(LobsterReportStatus.ERROR, contactFailure.status)
        assertEquals("CONTACT_UPDATE_FAILED", contactFailure.details.errorCode)
        assertEquals("update_phone_contact", contactFailure.action)
        assertEquals("reorder_home_apps", LobsterSettingEventFactory.homeAppsReordered().action)
        assertEquals("change_auto_answer_delay", LobsterSettingEventFactory.autoAnswerDelayChanged().action)
        assertEquals("change_dark_mode", LobsterSettingEventFactory.darkModeChanged().action)
        assertEquals("set_default_launcher", LobsterSettingEventFactory.defaultLauncherResult(true).action)
    }
}
