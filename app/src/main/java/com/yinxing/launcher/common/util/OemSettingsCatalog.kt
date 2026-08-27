package com.yinxing.launcher.common.util

import java.util.Locale

enum class OemSettingsCapability {
    AUTO_START,
    BACKGROUND_START
}

data class OemSettingsTarget(
    val packageName: String? = null,
    val className: String? = null,
    val action: String? = null,
    val packageExtraKey: String? = null
)

object OemSettingsCatalog {
    fun targets(
        manufacturer: String,
        capability: OemSettingsCapability
    ): List<OemSettingsTarget> {
        return when (manufacturer.lowercase(Locale.ROOT)) {
            "vivo", "iqoo" -> vivoTargets()
            "xiaomi", "redmi", "poco" -> xiaomiTargets(capability)
            "huawei" -> huaweiTargets()
            "honor" -> honorTargets()
            "oppo", "realme", "oneplus" -> oplusTargets()
            "samsung" -> samsungTargets()
            else -> emptyList()
        }
    }

    private fun vivoTargets() = listOf(
        component(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "packagename"
        ),
        component(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.PurviewTabActivity",
            "packagename"
        )
    )

    private fun xiaomiTargets(capability: OemSettingsCapability): List<OemSettingsTarget> {
        val permissionEditor = OemSettingsTarget(
            packageName = "com.miui.securitycenter",
            action = "miui.intent.action.APP_PERM_EDITOR",
            packageExtraKey = "extra_pkgname"
        )
        val autoStart = component(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        return if (capability == OemSettingsCapability.AUTO_START) {
            listOf(autoStart, permissionEditor)
        } else {
            listOf(permissionEditor, autoStart)
        }
    }

    private fun huaweiTargets() = listOf(
        component(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        component(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
        )
    )

    private fun honorTargets() = listOf(
        component(
            "com.hihonor.systemmanager",
            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        component(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )
    )

    private fun oplusTargets() = listOf(
        component(
            "com.oplus.safecenter",
            "com.oplus.safecenter.permission.startup.StartupAppListActivity"
        ),
        component(
            "com.coloros.safecenter",
            "com.coloros.privacypermissionsentry.PermissionTopActivity"
        ),
        component(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        )
    )

    private fun samsungTargets() = listOf(
        component(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        ),
        component(
            "com.samsung.android.sm",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
    )

    private fun component(
        packageName: String,
        className: String,
        packageExtraKey: String? = null
    ) = OemSettingsTarget(
        packageName = packageName,
        className = className,
        packageExtraKey = packageExtraKey
    )
}
