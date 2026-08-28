package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.LinearLayout
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsClassificationTest {
    @Test
    fun overviewUsesUnifiedCompactCategories() {
        val activity = buildActivity()

        listOf(
            "联系人", "来电与接听", "来电诊断", "安全守护",
            "桌面与首页", "显示与外观", "权限管理", "后台运行",
            "天气", "系统与更新", "关于银杏"
        )
            .forEach { assertTrue(activity.findText(it).isNotEmpty()) }
        assertTrue(activity.findText("卡片样式").isEmpty())
        assertTrue(activity.findText("快捷设置").isEmpty())
    }

    @Test
    fun contactAndCallPagesOnlyContainRelatedFunctions() {
        val activity = buildActivity()

        activity.showScreen(SettingsScreen.Contacts)
        assertTrue(activity.findDetailText("电话联系人").isNotEmpty())
        assertTrue(activity.findDetailText("微信视频联系人").isNotEmpty())
        assertTrue(activity.findDetailText("首页应用").isEmpty())

        activity.showScreen(SettingsScreen.Calls)
        assertTrue(activity.findDetailText("来电自动接听").isNotEmpty())
        assertTrue(activity.findDetailText("最近来电状态").isEmpty())
        assertTrue(activity.findDetailText("当前手机来电适配").isEmpty())
        assertTrue(activity.findDetailText("跌倒检测").isEmpty())
        assertTrue(activity.findDetailText("整卡可点击拨打").isEmpty())

        activity.showScreen(SettingsScreen.from(null, "diagnostics"))
        assertTrue(activity.findDetailText("当前手机来电适配").isNotEmpty())
        assertTrue(activity.findDetailText("最近来电状态").isNotEmpty())
        assertTrue(activity.findDetailText("来电自动接听").isEmpty())
    }

    @Test
    fun safetyAndDesktopPagesOwnMovedFunctions() {
        val activity = buildActivity()

        activity.showScreen(SettingsScreen.from(null, "safety"))
        assertTrue(activity.findDetailText("跌倒检测").isNotEmpty())
        assertTrue(activity.findDetailText("紧急联系人").isNotEmpty())

        activity.showScreen(SettingsScreen.Device)
        assertTrue(activity.findDetailText("首页应用").isNotEmpty())
        assertTrue(activity.findDetailText("整卡可点击拨打").isNotEmpty())
        assertTrue(activity.findDetailText("显示模式").isEmpty())
        assertTrue(activity.findDetailText("低性能模式").isEmpty())
        assertTrue(activity.findDetailText("后台保活相关权限").isEmpty())

        activity.showScreen(SettingsScreen.from(null, "display"))
        assertTrue(activity.findDetailText("外观模式").isNotEmpty())
        assertTrue(activity.findDetailText("首页显示大小").isNotEmpty())
        assertTrue(activity.findDetailText("字体大小").isNotEmpty())
        assertTrue(activity.findDetailText("减少动态效果").isEmpty())
        assertTrue(activity.findDetailText("首页应用").isEmpty())
        val advancedEntry = activity.findViewById<LinearLayout>(R.id.settings_detail_rows_secondary)
        assertEquals(1, advancedEntry.childCount)
        assertTrue(activity.findText("高级设置").isNotEmpty())

        activity.showScreen(SettingsScreen.from(null, "advanced"))
        assertTrue(activity.findDetailText("锁定首页布局").isNotEmpty())
        assertTrue(activity.findDetailText("长按响应时间").isNotEmpty())
        assertTrue(activity.findDetailText("恢复默认首页布局").isNotEmpty())
        assertTrue(activity.findSecondaryDetailText("减少动态效果").isNotEmpty())
        assertTrue(activity.findSecondaryDetailText("导出诊断信息").isNotEmpty())
        assertEquals(3, activity.findViewById<LinearLayout>(R.id.settings_detail_rows).childCount)
        assertEquals(2, activity.findViewById<LinearLayout>(R.id.settings_detail_rows_secondary).childCount)
        assertTrue(activity.findDetailText("首页显示大小").isEmpty())
        assertTrue(activity.findDetailText("外观模式").isEmpty())
    }

    @Test
    fun permissionsBackgroundWeatherAndSystemAreIndependentPages() {
        val activity = buildActivity()

        activity.showScreen(SettingsScreen.from(null, "permissions"))
        assertTrue(activity.findDetailText("电话权限").isNotEmpty())
        assertTrue(activity.findDetailText("通知权限").isNotEmpty())
        assertTrue(activity.findDetailText("电池无限制").isEmpty())

        activity.showScreen(SettingsScreen.from(null, "background"))
        assertTrue(activity.findDetailText("电池无限制").isNotEmpty())
        assertTrue(activity.findDetailText("开机自启动").isNotEmpty())
        assertTrue(activity.findDetailText("电话权限").isEmpty())

        activity.showScreen(SettingsScreen.from(null, "weather"))
        assertTrue(activity.findDetailText("天气城市").isNotEmpty())
        assertTrue(activity.findDetailText("版本更新").isEmpty())

        activity.showScreen(SettingsScreen.System)
        assertTrue(activity.findDetailText("系统设置").isNotEmpty())
        assertTrue(activity.findDetailText("版本更新").isNotEmpty())
        assertTrue(activity.findDetailText("天气城市").isEmpty())
    }

    private fun buildActivity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.findText(text: String): List<View> = arrayListOf<View>().also {
        findViewById<View>(android.R.id.content).findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }

    private fun SettingsActivity.findDetailText(text: String): List<View> = arrayListOf<View>().also {
        findViewById<View>(R.id.settings_detail_rows).findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }

    private fun SettingsActivity.findSecondaryDetailText(text: String): List<View> =
        arrayListOf<View>().also {
            findViewById<View>(R.id.settings_detail_rows_secondary)
                .findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
        }
}
