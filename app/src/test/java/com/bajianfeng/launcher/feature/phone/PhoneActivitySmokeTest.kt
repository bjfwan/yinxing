package com.bajianfeng.launcher.feature.phone

import android.view.View
import android.widget.TextView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.ui.PageStateView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneActivitySmokeTest {
    @Test
    fun launchWithoutContactsPermissionShowsPermissionState() {
        val activity = Robolectric.buildActivity(PhoneActivity::class.java).setup().get()

        val stateLayout = activity.findViewById<PageStateView>(R.id.view_page_state)
        val stateTitle = activity.findViewById<TextView>(R.id.tv_page_state_title)

        assertEquals(View.VISIBLE, stateLayout.visibility)
        assertEquals(activity.getString(R.string.state_phone_permission_title), stateTitle.text.toString())
    }
}
