package com.yinxing.launcher.data.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import com.yinxing.launcher.feature.home.HomeAppItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherAppRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: LauncherPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        HomeAppConfig(context).clear()
        LauncherSettingsDataStore.getInstance(context).clear()
        preferences = LauncherPreferences(context)
    }

    @Test
    fun getInstalledAppsUsesInjectedSourceAndSelectionConfig() = runTest {
        preferences.setPackageSelected("pkg.beta", true)
        val repository = LauncherAppRepository(
            context,
            FakeLauncherAppSource(
                installed = listOf(
                    LauncherAppRecord("pkg.zeta", "Zeta"),
                    LauncherAppRecord("pkg.beta", "Beta")
                )
            )
        )

        val apps = repository.getInstalledApps(preferences)

        assertEquals(listOf("pkg.beta", "pkg.zeta"), apps.map { it.packageName })
        assertTrue(apps.first { it.packageName == "pkg.beta" }.isSelected)
    }

    @Test
    fun getHomeItemsUsesInjectedSourceAndSavedOrder() = runTest {
        preferences.setPackageSelected("pkg.alpha", true)
        preferences.setPackageSelected("pkg.beta", true)
        preferences.saveAppOrder(listOf("pkg.beta", "pkg.alpha"))
        val repository = LauncherAppRepository(
            context,
            FakeLauncherAppSource(
                selected = listOf(
                    LauncherAppRecord("pkg.alpha", "Alpha"),
                    LauncherAppRecord("pkg.beta", "Beta")
                )
            )
        )

        val appPackages = repository.getHomeItems(preferences)
            .filter { it.type == HomeAppItem.Type.APP }
            .map { it.packageName }

        assertEquals(listOf("pkg.beta", "pkg.alpha"), appPackages)
    }

    private class FakeLauncherAppSource(
        private val installed: List<LauncherAppRecord> = emptyList(),
        private val selected: List<LauncherAppRecord> = installed
    ) : LauncherAppSource {
        override suspend fun loadInstalledApps(): List<LauncherAppRecord> = installed

        override suspend fun loadSelectedApps(packageNames: Set<String>): List<LauncherAppRecord> {
            return selected.filter { it.packageName in packageNames }
        }
    }
}
