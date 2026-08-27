package com.yinxing.launcher.feature.videocall

import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoCallContactAdapterInteractionTest {
    @Test
    fun callModeOnlyExplicitVideoButtonStartsContact() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var actionCount = 0
        val adapter = VideoCallContactAdapter(
            scope = activity.lifecycleScope,
            lowPerformanceMode = true,
            onContactClick = { actionCount += 1 },
            onEditClick = {}
        )
        adapter.submitList(
            listOf(
                Contact(
                    id = "1",
                    name = "妈妈",
                    wechatId = "妈妈",
                    preferredAction = Contact.PreferredAction.WECHAT_VIDEO
                )
            )
        )
        shadowOf(Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(
            FrameLayout(ContextThemeWrapper(activity, R.style.Theme_OldLauncher)),
            0
        )
        adapter.onBindViewHolder(holder, 0)

        holder.itemView.performClick()
        assertEquals(0, actionCount)

        holder.itemView.findViewById<android.view.View>(R.id.btn_video_call).performClick()
        assertEquals(1, actionCount)
    }
}
