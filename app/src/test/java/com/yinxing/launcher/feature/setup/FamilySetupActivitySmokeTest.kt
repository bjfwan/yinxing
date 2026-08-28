package com.yinxing.launcher.feature.setup

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilySetupActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FamilySetupPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstScreenIsOneMinimalThreeItemChecklistForTheFamily() {
        val activity = Robolectric.buildActivity(FamilySetupActivity::class.java).setup().get()

        assertEquals("家属首次配置", activity.findViewById<TextView>(R.id.tv_setup_toolbar_title).text.toString())
        assertEquals("请家人完成3项设置", activity.findViewById<TextView>(R.id.tv_setup_title).text.toString())
        assertEquals("常用联系人", activity.findViewById<TextView>(R.id.tv_setup_contacts_title).text.toString())
        assertEquals("电话权限", activity.findViewById<TextView>(R.id.tv_setup_permission_title).text.toString())
        assertEquals("默认桌面", activity.findViewById<TextView>(R.id.tv_setup_launcher_title).text.toString())
        assertFalse(activity.findViewById<View>(R.id.btn_setup_finish).isEnabled)
    }

    @Test
    fun contactTaskOpensExistingPhoneContactManager() {
        val activity = Robolectric.buildActivity(FamilySetupActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.btn_setup_contacts).performClick()

        assertEquals(
            PhoneContactActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    @Test
    fun permissionTaskOpensExistingPermissionCenterAndReturnsToChecklist() {
        val activity = Robolectric.buildActivity(FamilySetupActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.btn_setup_permission).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals("permissions", intent.getStringExtra(SettingsActivity.EXTRA_SECTION))
        assertTrue(intent.getBooleanExtra(SettingsActivity.EXTRA_RETURN_TO_CALLER, false))
    }

    @Test
    fun defaultLauncherTaskOpensExistingDeviceSettingsAndReturnsToChecklist() {
        val activity = Robolectric.buildActivity(FamilySetupActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.btn_setup_launcher).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals("device", intent.getStringExtra(SettingsActivity.EXTRA_SECTION))
        assertTrue(intent.getBooleanExtra(SettingsActivity.EXTRA_RETURN_TO_CALLER, false))
    }

    @Test
    fun settingsBackReturnsDirectlyToFamilyChecklist() {
        val setup = Robolectric.buildActivity(FamilySetupActivity::class.java).setup().get()
        setup.findViewById<View>(R.id.btn_setup_permission).performClick()
        val settingsIntent = shadowOf(setup).nextStartedActivity
        val settings = Robolectric.buildActivity(SettingsActivity::class.java, settingsIntent).setup().get()

        settings.findViewById<View>(R.id.btn_detail_back).performClick()

        assertTrue(settings.isFinishing)
    }

    @Test
    fun familyCanReopenSetupFromSettings() {
        FamilySetupPreferences(context).markCompleted()
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        settings.findViewById<View>(R.id.btn_family_setup).performClick()

        assertEquals(
            FamilySetupActivity::class.java.name,
            shadowOf(settings).nextStartedActivity.component?.className,
        )
    }
}
