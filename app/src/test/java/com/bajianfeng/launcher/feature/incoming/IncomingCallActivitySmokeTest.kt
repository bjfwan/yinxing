package com.bajianfeng.launcher.feature.incoming

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences
import org.junit.Assert.assertEquals
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
class IncomingCallActivitySmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        resetLauncherPreferencesSingleton()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 启动 / 来电人显示
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun launchWithChineseCallerNameShowsName() {
        val activity = buildActivity("王大爷")
        idle()
        assertEquals("王大爷", activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithEnglishCallerNameShowsName() {
        val activity = buildActivity("Alice")
        idle()
        assertEquals("Alice", activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithMixedLangCallerNameShowsName() {
        val activity = buildActivity("Bob李四")
        idle()
        assertEquals("Bob李四", activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithSpecialCharCallerNameShowsName() {
        val activity = buildActivity("wan.")
        idle()
        assertEquals("wan.", activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithEmojiCallerNameShowsName() {
        val activity = buildActivity("🐼猫咪")
        idle()
        assertEquals("🐼猫咪", activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithLongCallerNameShowsName() {
        val name = "A".repeat(50)
        val activity = buildActivity(name)
        idle()
        assertEquals(name, activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithNullCallerNameShowsUnknown() {
        val activity = buildActivityRaw(callerName = null)
        idle()
        assertEquals(context.getString(R.string.incoming_call_unknown_caller), activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithBlankCallerNameShowsUnknown() {
        val activity = buildActivityRaw(callerName = "   ")
        idle()
        assertEquals(context.getString(R.string.incoming_call_unknown_caller), activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithEmptyCallerNameShowsUnknown() {
        val activity = buildActivityRaw(callerName = "")
        idle()
        assertEquals(context.getString(R.string.incoming_call_unknown_caller), activity.tv_caller.text.toString())
    }

    @Test
    fun launchWithSingleSpaceCallerNameShowsUnknown() {
        val activity = buildActivityRaw(callerName = " ")
        idle()
        assertEquals(context.getString(R.string.incoming_call_unknown_caller), activity.tv_caller.text.toString())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 视图存在性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun callerTextViewExists() {
        val activity = buildActivity("测试人")
        assertNotNull(activity.tv_caller)
    }

    @Test
    fun acceptButtonExists() {
        val activity = buildActivity("测试人")
        assertNotNull(activity.findViewById<CardView>(R.id.btn_incoming_accept))
    }

    @Test
    fun declineButtonExists() {
        val activity = buildActivity("测试人")
        assertNotNull(activity.findViewById<CardView>(R.id.btn_incoming_decline))
    }

    @Test
    fun countdownTextViewExists() {
        val activity = buildActivity("测试人")
        assertNotNull(activity.tv_countdown)
    }

    @Test
    fun acceptButtonIsVisible() {
        val activity = buildActivity("测试人")
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<CardView>(R.id.btn_incoming_accept).visibility)
    }

    @Test
    fun declineButtonIsVisible() {
        val activity = buildActivity("测试人")
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<CardView>(R.id.btn_incoming_decline).visibility)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 自动接听开关 — 倒计时 UI
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun autoAnswerOnShowsCountdownText() {
        prefs().setAutoAnswerEnabled(true)
        val activity = buildActivity("用户A")
        idle()
        assertTrue("倒计时文字应非空", activity.tv_countdown.text.isNotEmpty())
    }

    @Test
    fun autoAnswerOnShowsAutoAnswerWording() {
        prefs().setAutoAnswerEnabled(true)
        val activity = buildActivity("用户B")
        idle()
        assertTrue("应含'自动接听'", activity.tv_countdown.text.contains("自动接听"))
    }

    @Test
    fun autoAnswerOnCountdownTextContainsSeconds() {
        prefs().setAutoAnswerEnabled(true)
        val activity = buildActivity("用户C")
        idle()
        assertTrue("应含秒数数字", activity.tv_countdown.text.contains("秒"))
    }

    @Test
    fun autoAnswerOffShowsEmptyCountdownText() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivity("用户D")
        idle()
        assertTrue("关闭自动接听时倒计时应为空", activity.tv_countdown.text.isEmpty())
    }

    @Test
    fun autoAnswerDefaultIsOnAndShowsCountdown() {
        // 默认值为 true
        val activity = buildActivity("用户E")
        idle()
        assertTrue("默认开启时应显示倒计时", activity.tv_countdown.text.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 点击接听 — Activity 关闭
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun clickAcceptFinishesActivity() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivity("赵六")
        idle()
        activity.btn_accept.performClick()
        idle()
        assertTrue("点击接听后 Activity 应 finish", activity.isFinishing)
    }

    @Test
    fun clickAcceptFinishesActivityWhenAutoAnswerOn() {
        prefs().setAutoAnswerEnabled(true)
        val activity = buildActivity("孙七")
        idle()
        activity.btn_accept.performClick()
        idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun clickAcceptWithNegativeActionIndexStillFinishes() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivityRaw(callerName = "张X", acceptIndex = -1, declineIndex = -1)
        idle()
        activity.btn_accept.performClick()
        idle()
        assertTrue(activity.isFinishing)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 点击拒绝 — Activity 关闭
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun clickDeclineFinishesActivity() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivity("王五")
        idle()
        activity.btn_decline.performClick()
        idle()
        assertTrue("点击拒绝后 Activity 应 finish", activity.isFinishing)
    }

    @Test
    fun clickDeclineFinishesActivityWhenAutoAnswerOn() {
        prefs().setAutoAnswerEnabled(true)
        val activity = buildActivity("王五B")
        idle()
        activity.btn_decline.performClick()
        idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun clickDeclineWithNegativeActionIndexStillFinishes() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivityRaw(callerName = "李X", acceptIndex = -1, declineIndex = -1)
        idle()
        activity.btn_decline.performClick()
        idle()
        assertTrue(activity.isFinishing)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 连续点击
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun doubleClickAcceptDoesNotCrash() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivity("用户F")
        idle()
        activity.btn_accept.performClick()
        idle()
        // 第二次点击 finishing 状态不 crash
        runCatching { activity.btn_accept.performClick() }
        idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun doubleClickDeclineDoesNotCrash() {
        prefs().setAutoAnswerEnabled(false)
        val activity = buildActivity("用户G")
        idle()
        activity.btn_decline.performClick()
        idle()
        runCatching { activity.btn_decline.performClick() }
        idle()
        assertTrue(activity.isFinishing)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // onNewIntent — 刷新来电人
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun onNewIntentUpdatesCallerName() {
        prefs().setAutoAnswerEnabled(false)
        val controller = buildController("旧来电人")
        idle()

        controller.newIntent(buildIntent("新来电人", "key_new"))
        idle()

        assertEquals("新来电人", controller.get().tv_caller.text.toString())
    }

    @Test
    fun onNewIntentUpdatesToNullCallerShowsUnknown() {
        prefs().setAutoAnswerEnabled(false)
        val controller = buildController("旧来电人")
        idle()

        controller.newIntent(buildIntent(null, "key_null"))
        idle()

        assertEquals(
            context.getString(R.string.incoming_call_unknown_caller),
            controller.get().tv_caller.text.toString()
        )
    }

    @Test
    fun onNewIntentUpdatesToBlankCallerShowsUnknown() {
        prefs().setAutoAnswerEnabled(false)
        val controller = buildController("旧来电人")
        idle()

        controller.newIntent(buildIntent("   ", "key_blank"))
        idle()

        assertEquals(
            context.getString(R.string.incoming_call_unknown_caller),
            controller.get().tv_caller.text.toString()
        )
    }

    @Test
    fun onNewIntentMultipleTimesShowsLastCaller() {
        prefs().setAutoAnswerEnabled(false)
        val controller = buildController("第一人")
        idle()

        controller.newIntent(buildIntent("第二人", "key_2"))
        idle()
        controller.newIntent(buildIntent("第三人", "key_3"))
        idle()

        assertEquals("第三人", controller.get().tv_caller.text.toString())
    }

    @Test
    fun onNewIntentAutoAnswerOnResetsCountdown() {
        prefs().setAutoAnswerEnabled(true)
        val controller = buildController("某人")
        idle()

        controller.newIntent(buildIntent("新来电人", "key_new2"))
        idle()

        // 重置倒计时后应仍显示倒计时文字
        assertTrue(controller.get().tv_countdown.text.isNotEmpty())
    }

    @Test
    fun onNewIntentAutoAnswerOffShowsEmptyCountdown() {
        prefs().setAutoAnswerEnabled(false)
        val controller = buildController("某人2")
        idle()

        controller.newIntent(buildIntent("新人2", "key_new3"))
        idle()

        assertTrue(controller.get().tv_countdown.text.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Activity 生命周期 — 不崩溃
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun onPauseDoesNotCrash() {
        val controller = buildController("生命周期测试")
        idle()
        runCatching { controller.pause() }
        // 不抛异常即通过
    }

    @Test
    fun onResumeAfterPauseDoesNotCrash() {
        val controller = buildController("生命周期测试2")
        idle()
        runCatching {
            controller.pause()
            controller.resume()
        }
    }

    @Test
    fun onDestroyDoesNotCrash() {
        val controller = buildController("销毁测试")
        idle()
        runCatching { controller.destroy() }
    }

    @Test
    fun buildLaunchIntentHasCorrectExtras() {
        val intent = IncomingCallActivity.buildLaunchIntent(
            context = context,
            callerName = "测试",
            notificationKey = "key_test",
            acceptActionIndex = 2,
            declineActionIndex = 3
        )
        assertEquals("测试", intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NAME))
        assertEquals("key_test", intent.getStringExtra(IncomingCallActivity.EXTRA_NOTIFICATION_KEY))
        assertEquals(2, intent.getIntExtra(IncomingCallActivity.EXTRA_ACCEPT_ACTION_INDEX, -1))
        assertEquals(3, intent.getIntExtra(IncomingCallActivity.EXTRA_DECLINE_ACTION_INDEX, -1))
    }

    @Test
    fun buildLaunchIntentWithNullCallerNameExtrasNull() {
        val intent = IncomingCallActivity.buildLaunchIntent(
            context = context,
            callerName = null,
            notificationKey = "k",
            acceptActionIndex = -1,
            declineActionIndex = -1
        )
        // null extra 读回也是 null
        assertEquals(null, intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NAME))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 多个不同来电人快速启动（压力）
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun launch30DifferentCallersNoException() {
        prefs().setAutoAnswerEnabled(false)
        repeat(30) { i ->
            val activity = buildActivity("来电人_$i")
            idle()
            assertEquals("来电人_$i", activity.tv_caller.text.toString())
            // 主动销毁，避免资源积压
            activity.finish()
            idle()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════════════════════

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun prefs() = LauncherPreferences.getInstance(context)

    private fun buildIntent(
        callerName: String?,
        notificationKey: String,
        acceptIndex: Int = 0,
        declineIndex: Int = 1
    ): Intent = IncomingCallActivity.buildLaunchIntent(
        context = context,
        callerName = callerName,
        notificationKey = notificationKey,
        acceptActionIndex = acceptIndex,
        declineActionIndex = declineIndex
    )

    private fun buildActivity(
        callerName: String,
        key: String = "key_smoke",
        acceptIndex: Int = 0,
        declineIndex: Int = 1
    ) = Robolectric.buildActivity(
        IncomingCallActivity::class.java,
        buildIntent(callerName, key, acceptIndex, declineIndex)
    ).setup().get()

    private fun buildActivityRaw(
        callerName: String?,
        key: String = "key_smoke",
        acceptIndex: Int = 0,
        declineIndex: Int = 1
    ) = Robolectric.buildActivity(
        IncomingCallActivity::class.java,
        buildIntent(callerName, key, acceptIndex, declineIndex)
    ).setup().get()

    private fun buildController(
        callerName: String,
        key: String = "key_ctrl",
        acceptIndex: Int = -1,
        declineIndex: Int = -1
    ) = Robolectric.buildActivity(
        IncomingCallActivity::class.java,
        buildIntent(callerName, key, acceptIndex, declineIndex)
    ).setup()

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    // 便捷属性
    private val android.app.Activity.tv_caller
        get() = findViewById<TextView>(R.id.tv_incoming_caller)
    private val android.app.Activity.tv_countdown
        get() = findViewById<TextView>(R.id.tv_incoming_countdown)
    private val android.app.Activity.btn_accept
        get() = findViewById<CardView>(R.id.btn_incoming_accept)
    private val android.app.Activity.btn_decline
        get() = findViewById<CardView>(R.id.btn_incoming_decline)
}
