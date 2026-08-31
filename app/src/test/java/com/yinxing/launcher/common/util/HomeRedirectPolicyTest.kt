package com.yinxing.launcher.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRedirectPolicyTest {
    @Test
    fun neverRedirectsUntilTheUserEnablesIt() {
        assertFalse(
            HomeRedirectPolicy.shouldRedirect(
                userEnabled = false,
                nativeHomeActive = false,
                manufacturer = "OPPO",
                packageName = "com.android.launcher",
                className = "com.android.launcher.Launcher",
                nowMs = 5_000L,
                lastRedirectAtMs = 0L
            )
        )
    }

    @Test
    fun supportsKnownSystemLaunchersAcrossManufacturers() {
        listOf(
            Triple("OPPO", "com.android.launcher", "com.android.launcher.Launcher"),
            Triple("vivo", "com.bbk.launcher2", "com.bbk.launcher2.Launcher"),
            Triple("Xiaomi", "com.miui.home", "com.miui.home.launcher.Launcher"),
            Triple("HUAWEI", "com.huawei.android.launcher", "com.huawei.android.launcher.unihome.UniHomeLauncher"),
            Triple("Samsung", "com.sec.android.app.launcher", "com.sec.android.app.launcher.Launcher")
        ).forEach { (manufacturer, packageName, className) ->
            assertTrue(
                "$manufacturer should be supported",
                HomeRedirectPolicy.shouldRedirect(
                    userEnabled = true,
                    nativeHomeActive = false,
                    manufacturer = manufacturer,
                    packageName = packageName,
                    className = className,
                    nowMs = 5_000L,
                    lastRedirectAtMs = 0L
                )
            )
        }
    }

    @Test
    fun ignoresSettingsUnknownLaunchersAndCooldown() {
        assertFalse(
            HomeRedirectPolicy.shouldRedirect(
                userEnabled = true,
                nativeHomeActive = false,
                manufacturer = "OPPO",
                packageName = "com.android.launcher",
                className = "com.android.launcher.settings.LauncherSettingsActivity",
                nowMs = 5_000L,
                lastRedirectAtMs = 0L
            )
        )
        assertFalse(
            HomeRedirectPolicy.shouldRedirect(
                userEnabled = true,
                nativeHomeActive = false,
                manufacturer = "unknown",
                packageName = "other.launcher",
                className = "other.launcher.Launcher",
                nowMs = 5_000L,
                lastRedirectAtMs = 0L
            )
        )
        assertFalse(
            HomeRedirectPolicy.shouldRedirect(
                userEnabled = true,
                nativeHomeActive = false,
                manufacturer = "realme",
                packageName = "com.android.launcher",
                className = "com.android.launcher.Launcher",
                nowMs = 5_000L,
                lastRedirectAtMs = 4_500L
            )
        )
    }

    @Test
    fun nativeDefaultHomeSuppressesFallbackOnStandardManufacturers() {
        assertFalse(
            HomeRedirectPolicy.shouldRedirect(
                userEnabled = true,
                nativeHomeActive = true,
                manufacturer = "Samsung",
                packageName = "com.sec.android.app.launcher",
                className = "com.sec.android.app.launcher.Launcher",
                nowMs = 5_000L,
                lastRedirectAtMs = 0L
            )
        )
    }
}
