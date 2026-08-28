package com.yinxing.launcher.feature.settings

import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsOverviewDuplicateEntryTest {
    @Test
    fun standardOverviewDoesNotRepeatTheVersionUpdateEntry() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val matches = arrayListOf<View>()

        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            matches,
            "检查版本更新",
            View.FIND_VIEWS_WITH_TEXT
        )

        assertTrue(matches.isEmpty())
    }
}
