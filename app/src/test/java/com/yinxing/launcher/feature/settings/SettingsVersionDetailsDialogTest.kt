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
    fun versionEntryOpensDetailsBeforeCheckingForUpdates() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val dialog = activity.showVersionDetailsDialog()

        assertEquals(
            "银杏",
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_app_name)).text.toString()
        )
        assertEquals(
            "v${BuildConfig.VERSION_NAME}",
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_name)).text.toString()
        )
        assertEquals(
            BuildConfig.VERSION_CODE.toString(),
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_code)).text.toString()
        )
        assertEquals(
            activity.getString(R.string.settings_update_not_checked),
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_update_status)).text.toString()
        )
        assertEquals(
            activity.getString(R.string.settings_update_release_title),
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_release_title)).text.toString()
        )
        assertEquals(
            activity.getString(R.string.settings_update_release_notes),
            requireNotNull(dialog.findViewById<TextView>(R.id.tv_version_release_notes)).text.toString()
        )
        dialog.dismiss()
    }
}
