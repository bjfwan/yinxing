package com.yinxing.launcher.feature.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.feature.setup.FamilySetupActivity
import com.yinxing.launcher.feature.setup.FamilySetupPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityFamilySetupSmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FamilySetupPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        WeatherPreferences.resetForTest()
        WeatherPreferences.getInstance(context).markInitialLocationPermissionRequested()
    }

    @Test
    fun freshInstallLaunchesFamilySetup() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(
            FamilySetupActivity::class.java.name,
            shadowOf(activity).nextStartedActivityForResult.intent.component?.className,
        )
    }

    @Test
    fun completedInstallOpensHomeWithoutLaunchingSetup() {
        FamilySetupPreferences(context).markCompleted()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }
}
