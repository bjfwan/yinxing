package com.yinxing.launcher.feature.incoming

import java.util.Locale

internal enum class OemIncomingRequirement {
    AutoStart,
    AssociatedStart,
    BackgroundPopup,
    BatteryUnrestricted,
    NeverSleep,
    SideLoadRiskControl,
    CompatibilityLayer
}

internal data class OemIncomingCallPolicy(
    val vendorKey: String,
    val vendorName: String,
    val requiresDefaultPhoneRole: Boolean,
    val requirements: Set<OemIncomingRequirement>
) {
    companion object {
        fun forManufacturer(manufacturer: String): OemIncomingCallPolicy {
            return when (manufacturer.trim().lowercase(Locale.ROOT)) {
                "xiaomi", "redmi", "poco" -> policy(
                    key = "xiaomi",
                    name = "小米 / 澎湃OS",
                    requiresDefaultPhoneRole = true,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted
                )
                "vivo", "iqoo" -> policy(
                    key = "vivo",
                    name = "vivo / OriginOS",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted,
                    OemIncomingRequirement.SideLoadRiskControl
                )
                "huawei" -> policy(
                    key = "huawei",
                    name = "华为 / HarmonyOS",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.AssociatedStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted,
                    OemIncomingRequirement.CompatibilityLayer
                )
                "honor" -> policy(
                    key = "honor",
                    name = "荣耀 / MagicOS",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.AssociatedStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted,
                    OemIncomingRequirement.SideLoadRiskControl
                )
                "oppo", "oneplus", "realme" -> policy(
                    key = "oplus",
                    name = "OPPO / 一加 / realme",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted
                )
                "samsung" -> policy(
                    key = "samsung",
                    name = "三星 / One UI",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.NeverSleep,
                    OemIncomingRequirement.SideLoadRiskControl
                )
                "meizu" -> policy(
                    key = "meizu",
                    name = "魅族 / Flyme",
                    requiresDefaultPhoneRole = false,
                    OemIncomingRequirement.AutoStart,
                    OemIncomingRequirement.BackgroundPopup,
                    OemIncomingRequirement.BatteryUnrestricted
                )
                else -> policy(key = "android", name = "Android")
            }
        }

        private fun policy(
            key: String,
            name: String,
            requiresDefaultPhoneRole: Boolean = false,
            vararg requirements: OemIncomingRequirement
        ) = OemIncomingCallPolicy(
            vendorKey = key,
            vendorName = name,
            requiresDefaultPhoneRole = requiresDefaultPhoneRole,
            requirements = requirements.toSet()
        )
    }
}
