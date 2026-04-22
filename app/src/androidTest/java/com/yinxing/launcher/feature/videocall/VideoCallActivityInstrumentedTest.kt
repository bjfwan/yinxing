package com.yinxing.launcher.feature.videocall

import android.widget.EditText
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.testutil.InstrumentationTestEnvironment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoCallActivityInstrumentedTest {
    @Before
    fun setUp() {
        InstrumentationTestEnvironment.resetAppState()
    }

    @Test
    fun emptyStateIsShownWhenNoVideoContactsExist() {
        ActivityScenario.launch(VideoCallActivity::class.java).use { scenario ->
            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "视频联系人空状态未展示"
            ) { activity ->
                activity.findViewById<com.yinxing.launcher.common.ui.PageStateView>(R.id.view_page_state).isVisible
            }

            scenario.onActivity { activity ->
                val title = activity.findViewById<TextView>(R.id.tv_page_state_title).text.toString()
                val action = activity.findViewById<TextView>(R.id.tv_page_state_action).text.toString()
                org.junit.Assert.assertEquals(
                    activity.getString(R.string.state_video_empty_title),
                    title
                )
                org.junit.Assert.assertEquals(
                    activity.getString(R.string.state_video_empty_action_manage),
                    action
                )
            }
        }
    }

    @Test
    fun manageModeSearchCanFilterToEmptyAndClearQuery() {
        InstrumentationTestEnvironment.seedVideoContacts(
            Contact(id = "c1", name = "张三", wechatId = "zhangsan")
        )

        ActivityScenario.launch(VideoCallActivity::class.java).use { scenario ->
            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "视频联系人列表未加载"
            ) { activity ->
                activity.findViewById<RecyclerView>(R.id.recycler_video_contacts).adapter?.itemCount == 1
            }

            scenario.onActivity { activity ->
                activity.findViewById<CardView>(R.id.btn_mode_action).performClick()
            }

            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "管理模式搜索栏未展示"
            ) { activity ->
                activity.findViewById<CardView>(R.id.layout_manage_search).isVisible
            }

            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.et_contact_search).setText("不存在")
            }

            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "搜索空结果状态未展示"
            ) { activity ->
                activity.findViewById<TextView>(R.id.tv_page_state_title).text.toString() ==
                    activity.getString(R.string.state_video_search_empty_title)
            }

            scenario.onActivity { activity ->
                val clearAction = activity.findViewById<TextView>(R.id.tv_page_state_action)
                clearAction.performClick()
            }

            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "清空搜索后联系人未恢复"
            ) { activity ->
                activity.findViewById<EditText>(R.id.et_contact_search).text.isNullOrEmpty() &&
                    activity.findViewById<RecyclerView>(R.id.recycler_video_contacts).adapter?.itemCount == 1
            }
        }
    }
}
