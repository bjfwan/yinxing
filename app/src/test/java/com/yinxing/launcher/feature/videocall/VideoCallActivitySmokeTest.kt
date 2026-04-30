package com.yinxing.launcher.feature.videocall

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.ContactSqliteStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoCallActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetContactManagerSingleton()
        ContactSqliteStore.deleteDatabase(context)
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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
        waitUntil {
            stateView.visibility == View.VISIBLE && messageView.text.isNotBlank()
        }

        assertEquals(View.VISIBLE, stateView.visibility)
        assertEquals(activity.getString(R.string.state_video_empty_title), titleView.text.toString())
        assertEquals(activity.getString(R.string.state_video_empty_message), messageView.text.toString())
        assertEquals(activity.getString(R.string.action_back_home), actionView.text.toString())
        assertEquals(activity.getString(R.string.action_manage), modeActionText.text.toString())
        assertEquals(View.GONE, searchLayout.visibility)
        assertEquals(View.VISIBLE, modeActionButton.visibility)
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
        waitUntil {
            searchLayout.visibility == View.VISIBLE && messageView.text.isNotBlank()
        }

        assertEquals(activity.getString(R.string.action_add), modeActionText.text.toString())
        assertEquals(View.VISIBLE, searchLayout.visibility)
        assertEquals(View.VISIBLE, modeActionButton.visibility)
        assertEquals(activity.getString(R.string.state_video_manage_empty_message), messageView.text.toString())
        assertEquals(activity.getString(R.string.state_video_empty_action_add), actionView.text.toString())
    }

    private fun resetContactManagerSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.contact.ContactManager").getDeclaredField("instance")
        field.isAccessible = true
        (field.get(null) as? com.yinxing.launcher.data.contact.ContactManager)?.close()
        field.set(null, null)
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) {
                return
            }
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
