package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsAboutScreenTest {
    @Test
    fun aboutSectionKeyOpensAboutScreen() {
        assertEquals("about", SettingsScreen.from(null, "about").key)
    }

    @Test
    fun standardOverviewShowsAboutEntry() {
        val activity = buildActivity()

        assertTrue(activity.findText("关于银杏").isNotEmpty())
    }

    @Test
    fun aboutScreenKeepsExistingInformationGroupedByTabs() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.from(null, "about"))

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_about_hero).visibility)
        assertEquals("银杏", activity.findViewById<TextView>(R.id.settings_about_hero_name).text.toString())
        assertTrue(
            activity.findViewById<TextView>(R.id.settings_about_hero_version).text.toString()
                .contains("v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_about_tabs).visibility)
        assertEquals(3, activity.findViewById<LinearLayout>(R.id.settings_detail_rows).childCount)
        assertEquals(0, activity.findViewById<LinearLayout>(R.id.settings_detail_rows_secondary).childCount)
        listOf("源码仓库", "联系作者", "反馈问题").forEach {
            assertTrue(activity.findText(it).isNotEmpty())
        }

        activity.findText("服务信息").first().performClick()
        listOf("隐私政策", "服务条款", "软件许可证", "使用说明").forEach {
            assertTrue(activity.findText(it).isNotEmpty())
        }
        assertTrue(activity.findText("当前版本").isEmpty())
        assertTrue(activity.findText("检查版本更新").isEmpty())
    }

    @Test
    fun aboutScreenLinksPrivacyPolicyAndTermsOfService() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.from(null, "about"))
        activity.findText("服务信息").first().performClick()

        assertTrue(activity.findText("隐私政策").isNotEmpty())
        assertTrue(activity.findText("服务条款").isNotEmpty())
    }

    @Test
    fun serviceTabSurvivesAboutScreenRefresh() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)
        activity.findViewById<View>(R.id.settings_about_tab_service).performClick()

        activity.detailController.bind(SettingsScreen.About)

        assertTrue(activity.findViewById<View>(R.id.settings_about_tab_service).isSelected)
        assertTrue(activity.findText("隐私政策").isNotEmpty())
    }

    @Test
    fun privacyAndTermsOpenInsideTheApp() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)
        activity.findViewById<View>(R.id.settings_about_tab_service).performClick()
        val rows = activity.findViewById<LinearLayout>(R.id.settings_detail_rows)

        rows.getChildAt(1).findViewById<View>(R.id.detail_row_click_target).performClick()
        val privacyIntent = shadowOf(activity).nextStartedActivity
        assertEquals(
            "com.yinxing.launcher.feature.settings.LegalDocumentActivity",
            privacyIntent.component?.className
        )
        assertEquals("privacy", privacyIntent.getStringExtra("legal_document_kind"))

        rows.getChildAt(2).findViewById<View>(R.id.detail_row_click_target).performClick()
        val termsIntent = shadowOf(activity).nextStartedActivity
        assertEquals(
            "com.yinxing.launcher.feature.settings.LegalDocumentActivity",
            termsIntent.component?.className
        )
        assertEquals("terms", termsIntent.getStringExtra("legal_document_kind"))
    }

    @Test
    fun softwareLicenseOpensAsOfflineInAppContent() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)
        activity.findViewById<View>(R.id.settings_about_tab_service).performClick()
        val rows = activity.findViewById<LinearLayout>(R.id.settings_detail_rows)

        rows.getChildAt(3).findViewById<View>(R.id.detail_row_click_target).performClick()

        assertTrue(shadowOf(activity).nextStartedActivity == null)
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertTrue(dialog.window!!.decorView.findText("GNU General Public License v3.0").isNotEmpty())
        assertTrue(dialog.window!!.decorView.findText("全部历史原创代码").isNotEmpty())
        dialog.dismiss()
    }

    @Test
    fun userReportDialogRequiresDescription() {
        val activity = buildActivity()
        val dialog = activity.detailController.showUserReportDialog()

        dialog.findViewById<View>(R.id.user_report_submit)!!.performClick()

        assertTrue(dialog.isShowing)
        assertEquals(View.VISIBLE, dialog.findViewById<View>(R.id.user_report_error)!!.visibility)
        dialog.findViewById<EditText>(R.id.user_report_description)!!.setText("首页按钮点不动")
        dialog.findViewById<View>(R.id.user_report_submit)!!.performClick()
        assertTrue(!dialog.isShowing)
    }

    @Test
    fun userReportKeepsDeviceDetailsBehindDisclosure() {
        val activity = buildActivity()
        val dialog = activity.detailController.showUserReportDialog()
        val details = dialog.findViewById<View>(R.id.user_report_privacy_details)!!
        val toggle = dialog.findViewById<TextView>(R.id.user_report_privacy_toggle)!!

        assertEquals(View.GONE, details.visibility)
        toggle.performClick()
        assertEquals(View.VISIBLE, details.visibility)
        assertEquals("收起说明", toggle.text.toString())
        dialog.dismiss()
    }

    @Test
    fun userReportTypeUsesTheUnifiedSingleChoiceDialog() {
        val activity = buildActivity()
        val reportDialog = activity.detailController.showUserReportDialog()
        val typeField = reportDialog.findViewById<View>(R.id.user_report_type)!!

        assertTrue(typeField is TextView)
        assertEquals("功能问题", (typeField as TextView).text.toString())
        typeField.performClick()

        val choiceDialog = ShadowDialog.getLatestDialog()
        assertTrue(choiceDialog !== reportDialog)
        listOf("功能问题", "显示问题", "卡顿或速度慢", "闪退或无响应", "其他问题").forEach {
            assertTrue(choiceDialog.window!!.decorView.findText(it).isNotEmpty())
        }
        choiceDialog.dismiss()
        reportDialog.dismiss()
    }

    private fun buildActivity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.findText(text: String): List<View> = arrayListOf<View>().also {
        findViewById<View>(android.R.id.content).findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }

    private fun View.findText(text: String): List<View> = arrayListOf<View>().also {
        findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }
}
