package com.bajianfeng.launcher.feature.appmanage

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import org.junit.Assert.assertNotNull
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
class AppManageActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        LauncherPreferences.getInstance(context).setLowPerformanceModeEnabled(true)
        LauncherAppRepository.getInstance(context).invalidateInstalledApps()
        LauncherAppRepository.getInstance(context).invalidateSelections()
        registerLauncherApp("com.example.clock", "时钟")
    }

    @Test
    fun launchLoadsAppListAndAppliesLowPerformanceMode() {
        val activity = Robolectric.buildActivity(AppManageActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)

        waitFor { recyclerView.adapter?.itemCount ?: 0 > 0 }

        assertNotNull(recyclerView.adapter)
        assertTrue(recyclerView.layoutManager is LinearLayoutManager)
        assertTrue((recyclerView.adapter?.itemCount ?: 0) > 0)
        assertTrue(recyclerView.itemAnimator == null)
    }

    @Suppress("DEPRECATION")
    private fun registerLauncherApp(packageName: String, label: String) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                return
            }
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
