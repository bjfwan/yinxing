package com.bajianfeng.launcher.feature.videocall

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoCallActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE).edit().clear().commit()
        resetContactManagerSingleton()
    }

    @Test
    fun launchShowsCallModeEmptyState() {
        val activity = Robolectric.buildActivity(VideoCallActivity::class.java).setup().get()
        val stateView = activity.findViewById<View>(R.id.view_page_state)
        val titleView = activity.findViewById<TextView>(R.id.tv_page_state_title)
        val messageView = activity.findViewById<TextView>(R.id.tv_page_state_message)
        val actionView = activity.findViewById<TextView>(R.id.tv_page_state_action)
        val searchLayout = activity.findViewById<CardView>(R.id.layout_manage_search)
        val modeActionButton = activity.findViewById<CardView>(R.id.btn_mode_action)
        val modeActionText = activity.findViewById<TextView>(R.id.tv_mode_action)

        assertEquals(View.VISIBLE, stateView.visibility)
        assertEquals(activity.getString(R.string.state_video_empty_title), titleView.text.toString())
        assertEquals(activity.getString(R.string.state_video_empty_message), messageView.text.toString())
        assertEquals(activity.getString(R.string.action_back_home), actionView.text.toString())
        assertEquals(activity.getString(R.string.action_add), modeActionText.text.toString())
        assertEquals(View.GONE, searchLayout.visibility)
        assertEquals(View.GONE, modeActionButton.visibility)
    }

    @Test
    fun launchInManageModeShowsManageEmptyState() {
        val intent = VideoCallActivity.createIntent(context, startInManageMode = true)
        val activity = Robolectric.buildActivity(VideoCallActivity::class.java, intent).setup().get()
        val messageView = activity.findViewById<TextView>(R.id.tv_page_state_message)
        val actionView = activity.findViewById<TextView>(R.id.tv_page_state_action)
        val searchLayout = activity.findViewById<CardView>(R.id.layout_manage_search)
        val modeActionButton = activity.findViewById<CardView>(R.id.btn_mode_action)
        val modeActionText = activity.findViewById<TextView>(R.id.tv_mode_action)

        assertEquals(activity.getString(R.string.action_add), modeActionText.text.toString())
        assertEquals(View.VISIBLE, searchLayout.visibility)
        assertEquals(View.VISIBLE, modeActionButton.visibility)
        assertEquals(activity.getString(R.string.state_video_manage_empty_message), messageView.text.toString())
        assertEquals(activity.getString(R.string.state_video_empty_action_add), actionView.text.toString())
    }


    private fun resetContactManagerSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.contact.ContactManager").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
