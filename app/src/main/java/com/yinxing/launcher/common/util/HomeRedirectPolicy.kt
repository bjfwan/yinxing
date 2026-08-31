package com.yinxing.launcher.common.util

import java.util.Locale

object HomeRedirectPolicy {
    private const val REDIRECT_COOLDOWN_MS = 1_500L

    private val launcherPackagesByVendor = mapOf(
        "oplus" to setOf(
            "com.android.launcher",
            "com.oplus.launcher",
            "com.oppo.launcher",
            "net.oneplus.launcher"
        ),
        "vivo" to setOf("com.bbk.launcher2", "com.vivo.launcher"),
        "xiaomi" to setOf("com.miui.home"),
        "huawei" to setOf("com.huawei.android.launcher"),
        "honor" to setOf("com.hihonor.android.launcher"),
        "samsung" to setOf("com.sec.android.app.launcher"),
        "google" to setOf("com.google.android.apps.nexuslauncher")
    )

    private val genericLauncherPackages = setOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3"
    )

    fun shouldRedirect(
        userEnabled: Boolean,
        nativeHomeActive: Boolean,
        manufacturer: String,
        packageName: String?,
        className: String?,
        nowMs: Long,
        lastRedirectAtMs: Long
    ): Boolean {
        if (!userEnabled || packageName == null || className == null) return false
        val profile = OemLauncherPolicy.profile(manufacturer)
        if (nativeHomeActive && profile.vendorKey != "oplus") return false
        val knownPackages = launcherPackagesByVendor[profile.vendorKey].orEmpty() +
            genericLauncherPackages
        if (packageName !in knownPackages) return false

        val normalizedClass = className.lowercase(Locale.ROOT)
        if ("settings" in normalizedClass || "setting" in normalizedClass) return false
        if (!normalizedClass.substringAfterLast('.').contains("launcher")) return false

        return lastRedirectAtMs <= 0L || nowMs - lastRedirectAtMs >= REDIRECT_COOLDOWN_MS
    }
}
