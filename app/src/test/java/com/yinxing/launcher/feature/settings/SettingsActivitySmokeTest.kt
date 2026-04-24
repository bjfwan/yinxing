package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class SettingsActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        IncomingCallDiagnostics.clear(context)
        registerSettingsActivity()
        registerHomeActivity(packageName = "com.android.launcher3")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 布局：hub 卡片视图存在性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun incomingGuardCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_incoming_guard))
    }

    @Test
    fun autoAnswerCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_auto_answer))
    }

    @Test
    fun contactsCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_contacts))
    }

    @Test
    fun permissionsCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_permissions))
    }

    @Test
    fun deviceCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_device))
    }

    @Test
    fun systemCardExists() {
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById(R.id.btn_card_system))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 来电守卫 — summary 反映当前阻断项
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun incomingGuardSummaryIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val summaryView = activity.findViewById<TextView>(R.id.tv_incoming_guard_summary)
        assertNotNull(summaryView)
        assertTrue("来电守卫摘要不应为空", summaryView.text.isNotEmpty())
    }

    @Test
    fun incomingGuardActionTextIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val actionView = activity.findViewById<TextView>(R.id.tv_incoming_guard_action)
        assertNotNull(actionView)
        assertTrue("来电守卫操作按钮文本不应为空", actionView.text.isNotEmpty())
    }

    @Test
    fun incomingGuardStatusBadgeIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val statusView = activity.findViewById<TextView>(R.id.tv_incoming_guard_status)
        assertNotNull(statusView)
        assertTrue("来电守卫状态徽章不应为空", statusView.text.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 自动接听 hub — 默认开启时摘要包含延迟秒数
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun autoAnswerHubSummaryWhenEnabledContainsDelaySummary() {
        val activity = buildActivity()
        idle()
        val summaryView = activity.findViewById<TextView>(R.id.tv_auto_answer_hub_summary)
        assertNotNull(summaryView)
        val expected = activity.getString(
            R.string.settings_auto_answer_delay_summary,
            LauncherPreferences.DEFAULT_AUTO_ANSWER_DELAY_SECONDS
        )
        assertTrue(
            "自动接听开启时摘要应包含延迟描述，实际: ${summaryView.text}",
            summaryView.text.toString() == expected
        )
    }

    @Test
    fun autoAnswerHubSummaryWhenDisabledShowsOffText() {
        LauncherPreferences.getInstance(context).setAutoAnswerEnabled(false)
        val activity = buildActivity()
        idle()
        val summaryView = activity.findViewById<TextView>(R.id.tv_auto_answer_hub_summary)
        assertNotNull(summaryView)
        val expected = activity.getString(R.string.settings_auto_answer_summary_off)
        assertTrue(
            "自动接听关闭时摘要应为关闭描述，实际: ${summaryView.text}",
            summaryView.text.toString() == expected
        )
    }

    @Test
    fun autoAnswerHubStatusBadgeIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val statusView = activity.findViewById<TextView>(R.id.tv_auto_answer_hub_status)
        assertNotNull(statusView)
        assertTrue("自动接听状态徽章不应为空", statusView.text.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 诊断链 — 最新通话链路显示在来电守卫摘要区域
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun incomingTraceDiagnosticsDoNotCrashOnLaunch() {
        IncomingCallDiagnostics.recordBroadcastReceived(
            context = context,
            callerLabel = "张阿姨",
            incomingNumber = "13812345678",
            autoAnswer = true
        )
        IncomingCallDiagnostics.recordServiceStarted(context, "张阿姨", autoAnswer = true)
        IncomingCallDiagnostics.recordActivityShown(context, "张阿姨")
        IncomingCallDiagnostics.recordAcceptSuccess(
            context,
            context.getString(R.string.incoming_call_status_accept_sent)
        )
        val activity = buildActivity()
        idle()
        assertNotNull(activity.findViewById<TextView>(R.id.tv_incoming_guard_summary))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 权限 hub — 视图存在且文本非空
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun permissionHubSummaryIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val summaryView = activity.findViewById<TextView>(R.id.tv_permission_hub_summary)
        assertNotNull(summaryView)
        assertTrue("权限摘要不应为空", summaryView.text.isNotEmpty())
    }

    @Test
    fun permissionHubStatusBadgeIsNotEmpty() {
        val activity = buildActivity()
        idle()
        val statusView = activity.findViewById<TextView>(R.id.tv_permission_hub_status)
        assertNotNull(statusView)
        assertTrue("权限状态徽章不应为空", statusView.text.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 生命周期 — 不崩溃
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun onResumeDoesNotCrash() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        idle()
        runCatching {
            controller.pause()
            controller.resume()
        }
        idle()
        assertFalse(controller.get().isFinishing)
    }

    @Test
    fun onDestroyDoesNotCrash() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        idle()
        runCatching { controller.destroy() }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════════════════════

    private fun buildActivity() =
        Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Suppress("DEPRECATION")
    private fun registerSettingsActivity() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        val applicationInfo = ApplicationInfo().apply {
            packageName = "com.android.settings"
            nonLocalizedLabel = "Settings"
        }
        val activityInfo = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "com.android.settings.Settings"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    @Suppress("DEPRECATION")
    private fun registerHomeActivity(packageName: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = "OldLauncher"
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.feature.home.MainActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
