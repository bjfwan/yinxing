package com.bajianfeng.launcher.feature.home

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.feature.phone.PhoneActivity
import com.bajianfeng.launcher.feature.videocall.VideoCallActivity
import com.bajianfeng.launcher.testutil.InstrumentationTestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Before
    fun setUp() {
        InstrumentationTestEnvironment.resetAppState()
        InstrumentationTestEnvironment.primeLauncherRepositoryWithBuiltInOnlyHome()
    }

    @Test
    fun launchShowsBuiltInHomeItemsAndClock() {
        val activity = launchMainActivity()
        try {
            waitUntil(activity, message = "主页未加载出内置入口") {
                it.findViewById<RecyclerView>(R.id.recycler_home).adapter?.itemCount == 5
            }

            runOnMainSync {
                val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
                val timeText = activity.findViewById<android.widget.TextView>(R.id.tv_time).text
                val dateText = activity.findViewById<android.widget.TextView>(R.id.tv_date).text
                assertEquals(5, recyclerView.adapter?.itemCount)
                assertTrue(timeText.isNotBlank())
                assertTrue(dateText.isNotBlank())
            }
        } finally {
            finishActivity(activity)
        }
    }

    @Test
    fun clickPhoneEntryOpensPhoneActivity() {
        launchAndOpenHomeEntry(
            labelResId = R.string.home_item_phone,
            expectedActivity = PhoneActivity::class.java,
            failureMessage = "点击电话入口后未进入 PhoneActivity"
        )
    }

    @Test
    fun clickWechatVideoEntryOpensVideoCallActivity() {
        launchAndOpenHomeEntry(
            labelResId = R.string.home_item_wechat_video,
            expectedActivity = VideoCallActivity::class.java,
            failureMessage = "点击微信视频入口后未进入 VideoCallActivity"
        )
    }

    private fun launchAndOpenHomeEntry(
        labelResId: Int,
        expectedActivity: Class<out Activity>,
        failureMessage: String
    ) {
        val activity = launchMainActivity()
        try {
            waitUntil(activity, message = "主页入口未准备完成") {
                it.findViewById<RecyclerView>(R.id.recycler_home).adapter?.itemCount == 5
            }

            val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(labelResId)
            runOnMainSync {
                val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
                val clickableView = findViewWithContentDescription(recyclerView, label)
                assertNotNull("未找到首页入口：$label", clickableView)
                checkNotNull(clickableView).performClick()
            }
            InstrumentationTestEnvironment.waitForActivityInAnyStage(
                expectedActivity = expectedActivity,
                message = failureMessage
            )
        } finally {
            finishActivity(activity)
        }
    }

    private fun launchMainActivity(): MainActivity {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return InstrumentationRegistry.getInstrumentation().startActivitySync(intent) as MainActivity
    }

    private fun waitUntil(
        activity: MainActivity,
        message: String,
        timeoutMs: Long = 3_000,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && android.os.SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            runOnMainSync {
                matched = condition(activity)
            }
            if (!matched) {
                android.os.SystemClock.sleep(50)
            }
        }
        assertTrue(message, matched)
    }

    private fun runOnMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun finishActivity(activity: Activity) {
        runOnMainSync {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.finish()
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun findViewWithContentDescription(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) {
            return root
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findViewWithContentDescription(root.getChildAt(index), description)?.let { found ->
                    return found
                }
            }
        }
        return null
    }
}
