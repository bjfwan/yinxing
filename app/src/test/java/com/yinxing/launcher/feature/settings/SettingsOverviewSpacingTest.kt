package com.yinxing.launcher.feature.settings

import android.view.View
import android.view.ViewGroup
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsOverviewSpacingTest {
    @Test
    fun categoriesHaveBreathingRoomInsideAndBetweenGroups() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val expectedGroupSpacing = activity.dp(20)

        listOf(
            R.id.btn_detail_contacts,
            R.id.btn_detail_device,
            R.id.btn_detail_permissions,
            R.id.btn_detail_weather
        ).forEach { entryId ->
            val entry = activity.findViewById<View>(entryId)
            val card = entry.parent.parent as View
            val params = card.layoutParams as ViewGroup.MarginLayoutParams

            assertEquals(expectedGroupSpacing, params.topMargin)
        }

        val firstEntry = activity.findViewById<View>(R.id.btn_detail_contacts)
        assertEquals(activity.dp(60), firstEntry.minimumHeight)
        assertEquals(activity.dp(8), firstEntry.paddingTop)
        assertEquals(activity.dp(8), firstEntry.paddingBottom)
    }

    private fun SettingsActivity.dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
