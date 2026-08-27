package com.yinxing.launcher.feature.settings

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class SettingsNavigationSmokeTest {
    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("launcher_prefs", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun opensStandardSettingsByDefaultAndCanSwitchToElderMode() {
        val activity = buildActivity()

        assertEquals("设置", activity.findViewById<TextView>(R.id.settings_page_title).text.toString())
        activity.findViewById<View>(R.id.btn_switch_mode).performClick()
        idle()

        assertEquals("老人设置", activity.findViewById<TextView>(R.id.settings_page_title).text.toString())
    }

    @Test
    fun standardSettingUsesSecondaryPages() {
        val activity = buildActivity()

        activity.findViewById<View>(R.id.btn_detail_contacts).performClick()
        idle()

        assertEquals("联系人与首页", activity.findViewById<TextView>(R.id.settings_page_title).text.toString())
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals("设置", activity.findViewById<TextView>(R.id.settings_page_title).text.toString())
    }

    @Test
    fun elderContactEntryUsesCenteredDialog() {
        val activity = buildActivity()
        activity.findViewById<View>(R.id.btn_switch_mode).performClick()
        idle()

        activity.findViewById<View>(R.id.btn_elder_contacts).performClick()
        idle()

        assertNotNull(ShadowDialog.getLatestDialog())
    }

    private fun buildActivity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
