package com.yinxing.launcher.common.util

import android.provider.Settings
import java.util.Locale

enum class OemLauncherSupport {
    STANDARD,
    EXTRA_SECURITY_GATE,
    VERSION_DEPENDENT,
    RESTRICTED
}

data class OemLauncherProfile(
    val vendorKey: String,
    val support: OemLauncherSupport,
    val iconPackages: List<String>
) {
    val secondarySettingsAction: String?
        get() = if (support == OemLauncherSupport.EXTRA_SECURITY_GATE) {
            Settings.ACTION_SECURITY_SETTINGS
        } else {
            null
        }
}

object OemLauncherPolicy {
    fun profile(manufacturer: String): OemLauncherProfile {
        return when (manufacturer.lowercase(Locale.ROOT)) {
            "vivo", "iqoo" -> profile(
                "vivo",
                OemLauncherSupport.EXTRA_SECURITY_GATE,
                "com.bbk.launcher2",
                "com.vivo.permissionmanager"
            )
            "xiaomi", "redmi", "poco" -> profile(
                "xiaomi",
                OemLauncherSupport.RESTRICTED,
                "com.miui.home",
                "com.miui.securitycenter"
            )
            "huawei" -> profile(
                "huawei",
                OemLauncherSupport.VERSION_DEPENDENT,
                "com.huawei.android.launcher",
                "com.huawei.systemmanager"
            )
            "honor" -> profile(
                "honor",
                OemLauncherSupport.VERSION_DEPENDENT,
                "com.hihonor.android.launcher",
                "com.hihonor.systemmanager"
            )
            "oppo", "realme", "oneplus" -> profile(
                "oplus",
                OemLauncherSupport.STANDARD,
                "com.oplus.launcher",
                "com.oppo.launcher",
                "net.oneplus.launcher",
                "com.android.launcher"
            )
            "samsung" -> profile(
                "samsung",
                OemLauncherSupport.STANDARD,
                "com.sec.android.app.launcher",
                "com.samsung.android.lool"
            )
            "google" -> profile(
                "google",
                OemLauncherSupport.STANDARD,
                "com.google.android.apps.nexuslauncher",
                "com.android.settings"
            )
            else -> profile(
                "android",
                OemLauncherSupport.STANDARD,
                "com.android.settings"
            )
        }
    }

    private fun profile(
        vendorKey: String,
        support: OemLauncherSupport,
        vararg iconPackages: String
    ) = OemLauncherProfile(vendorKey, support, iconPackages.toList())
}
