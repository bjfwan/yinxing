package com.yinxing.launcher.feature.settings

import android.widget.TextView
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsVersionDetailsDialogTest {
    @Test
    fun updatePageShowsCurrentVersionAndStartsChecking() {
        val activity = Robolectric.buildActivity(AppUpdateActivity::class.java).setup().get()

        assertEquals(
            "v${BuildConfig.VERSION_NAME}",
            activity.findViewById<TextView>(R.id.tv_version_name).text.toString()
        )
        assertEquals(
            BuildConfig.VERSION_CODE.toString(),
            activity.findViewById<TextView>(R.id.tv_version_code).text.toString()
        )
        assertEquals(
            activity.getString(R.string.settings_update_checking),
            activity.findViewById<TextView>(R.id.tv_version_update_status).text.toString()
        )
        activity.finish()
    }
}
