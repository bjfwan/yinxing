package com.yinxing.launcher.feature.phone

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
class PhoneContactAdapterInteractionTest {
    @Test
    fun callModeOnlyExplicitCallButtonStartsCall() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var callCount = 0
        val adapter = PhoneContactAdapter(
            scope = activity.lifecycleScope,
            onCallClick = { callCount += 1 },
            onEditClick = {}
        )
        adapter.submitList(listOf(Contact(id = "1", name = "妈妈", phoneNumber = "13800138000")))
        shadowOf(Looper.getMainLooper()).idle()

        val holder = adapter.onCreateViewHolder(
            FrameLayout(ContextThemeWrapper(activity, R.style.Theme_OldLauncher)),
            adapter.getItemViewType(0)
        )
        adapter.onBindViewHolder(holder, 0)

        holder.itemView.performClick()
        assertEquals(0, callCount)

        holder.itemView.findViewById<android.view.View>(R.id.btn_call).performClick()
        assertEquals(1, callCount)
    }
}
