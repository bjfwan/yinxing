package com.bajianfeng.launcher.feature.home

import android.content.Context
import android.os.Looper
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        LauncherPreferences.getInstance(context).setLowPerformanceModeEnabled(false)
        LauncherAppRepository.getInstance(context).invalidateInstalledApps()
        LauncherAppRepository.getInstance(context).invalidateSelections()
    }

    @Test
    fun launchShowsBuiltInHomeItemsAndClock() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        val timeView = activity.findViewById<TextView>(R.id.tv_time)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if ((recyclerView.adapter?.itemCount ?: 0) >= 5) {
                return@repeat
            }
            Thread.sleep(50)
        }

        assertEquals(5, recyclerView.adapter?.itemCount)
        assertTrue(timeView.text.isNotBlank())
    }

    @Test
    fun lowPerformancePreferenceChangeUpdatesRecyclerBehavior() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
        val adapter = readField(activity, "adapter")
        val itemMoveCallback = readField(activity, "itemMoveCallback")

        waitForHomeItems(recyclerView, 5)

        assertTrue(recyclerView.itemAnimator is DefaultItemAnimator)
        assertFalse(readBooleanField(adapter, "lowPerformanceMode"))
        assertTrue(readBooleanField(itemMoveCallback, "animateDrag"))

        LauncherPreferences.getInstance(context).setLowPerformanceModeEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(recyclerView.itemAnimator == null)
        assertTrue(readBooleanField(adapter, "lowPerformanceMode"))
        assertFalse(readBooleanField(itemMoveCallback, "animateDrag"))
    }

    private fun waitForHomeItems(recyclerView: RecyclerView, expectedCount: Int) {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if ((recyclerView.adapter?.itemCount ?: 0) >= expectedCount) {
                return
            }
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun readField(target: Any, fieldName: String): Any {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)!!
    }

    private fun readBooleanField(target: Any, fieldName: String): Boolean {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.getBoolean(target)
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences").getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
