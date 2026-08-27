package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.R

internal enum class SettingsScreen(val key: String?, val titleRes: Int) {
    StandardOverview(null, R.string.settings_title),
    ElderOverview(null, R.string.settings_elder_title),
    Contacts("contacts", R.string.settings_contacts_title),
    Calls("calls", R.string.settings_calls_title),
    Permissions("permissions", R.string.settings_permissions_title),
    Device("device", R.string.settings_device_title),
    System("system", R.string.settings_section_system_title);

    companion object {
        fun from(mode: String?, section: String?): SettingsScreen {
            return section?.let { key -> entries.firstOrNull { it.key == key } }
                ?: if (mode == "elder") ElderOverview else StandardOverview
        }
    }
}
