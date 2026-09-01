package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTransparencyScreenTest {
    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("launcher_prefs", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun aboutScreenUsesThreeTabsAndShowsProjectStoryByDefault() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)

        listOf("项目", "设计与验证", "服务信息").forEach {
            assertTrue(activity.findText(it).isNotEmpty())
        }
        assertTrue(activity.findText("给爷爷做的简洁桌面").isNotEmpty())
    }

    @Test
    fun projectTabRemovesRepeatedCopyAndUsesCompactTabs() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)

        val tabs = listOf(
            R.id.settings_about_tab_project,
            R.id.settings_about_tab_evidence,
            R.id.settings_about_tab_service
        ).map { id -> activity.findViewById<View>(id) }

        tabs.forEach { tab ->
            assertTrue(tab.minimumHeight <= activity.resources.displayMetrics.density * 48)
            assertTrue((tab.layoutParams as LinearLayout.LayoutParams).weight == 1f)
        }
        assertTrue(activity.findText("银杏从家里的实际使用需求出发").isEmpty())
        assertTrue(activity.findText("开源 · 免费 · 无广告").isEmpty())
        assertTrue(activity.findText(activity.getString(R.string.settings_about_source_summary)).isEmpty())
        assertTrue(activity.findText(activity.getString(R.string.settings_user_report_summary)).isEmpty())
    }

    @Test
    fun designTabShowsGroundedFallBenchmarkAndVerificationLimits() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)

        activity.findText("设计与验证").first().performClick()

        listOf(
            "UMAFall 公开数据回放",
            "19 人 · 746 段记录 · 185 万个采样点",
            "94.23%",
            "97.03%",
            "不能代表真机准确率"
        ).forEach { assertTrue(activity.findText(it).isNotEmpty()) }
    }

    @Test
    fun currentVersionDetailsShowsReleaseHighlights() {
        val activity = buildActivity()
        val dialog = activity.showVersionDetailsDialog()

        listOf(
            "本版更新",
            "微信视频主动拨打升级为六条可组合路线",
            "重新选择剩余操作更少的安全入口",
            "失败时自动换线",
            "全面统一新银杏图标"
        ).forEach { text ->
            assertTrue(
                dialog.window?.decorView
                    ?.let { root -> arrayListOf<View>().also { root.findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT) } }
                    ?.isNotEmpty() == true
            )
        }
        dialog.dismiss()
    }

    private fun buildActivity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.findText(text: String): List<View> = arrayListOf<View>().also {
        findViewById<View>(android.R.id.content).findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }
}
