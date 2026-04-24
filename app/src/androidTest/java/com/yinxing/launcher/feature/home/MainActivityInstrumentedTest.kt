package com.yinxing.launcher.feature.home

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import com.yinxing.launcher.R
import com.yinxing.launcher.feature.phone.PhoneContactActivity

import com.yinxing.launcher.feature.videocall.VideoCallActivity
import com.yinxing.launcher.testutil.InstrumentationTestEnvironment
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
        launchMainActivityScenario().use { scenario ->
            InstrumentationTestEnvironment.waitUntil(scenario, message = "主页未加载出内置入口") {
                it.findViewById<RecyclerView>(R.id.recycler_home).adapter?.itemCount == 3
            }

            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
                val timeText = activity.findViewById<android.widget.TextView>(R.id.tv_time).text
                val dateText = activity.findViewById<android.widget.TextView>(R.id.tv_date).text
                assertEquals(3, recyclerView.adapter?.itemCount)
                assertTrue(timeText.isNotBlank())
                assertTrue(dateText.isNotBlank())
            }
        }
    }


    @Test
    fun clickPhoneEntryOpensPhoneContactActivity() {
        launchAndOpenHomeEntry(
            labelResId = R.string.home_item_phone,
            expectedActivity = PhoneContactActivity::class.java,
            failureMessage = "点击电话簿入口后未进入电话联系人页"
        )
    }


    @Test
    fun clickWechatVideoEntryOpensUnifiedContactActivity() {
        launchAndOpenHomeEntry(
            labelResId = R.string.home_item_wechat_video,
            expectedActivity = VideoCallActivity::class.java,
            failureMessage = "点击微信视频入口后未进入统一联系人页"
        )
    }

    private fun launchAndOpenHomeEntry(
        labelResId: Int,
        expectedActivity: Class<out Activity>,
        failureMessage: String
    ) {
        launchMainActivityScenario().use { scenario ->
            InstrumentationTestEnvironment.waitUntil(scenario, message = "主页入口未准备完成") {
                it.findViewById<RecyclerView>(R.id.recycler_home).adapter?.itemCount == 3
            }

            val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(labelResId)
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
                val clickableView = findViewWithContentDescription(recyclerView, label)
                assertNotNull("未找到首页入口：$label", clickableView)
                checkNotNull(clickableView).performClick()
            }
            InstrumentationTestEnvironment.waitForActivityInAnyStage(
                expectedActivity = expectedActivity,
                message = failureMessage
            )
        }
    }


    private fun launchMainActivityScenario(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return ActivityScenario.launch(intent)
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
