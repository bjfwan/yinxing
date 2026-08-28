package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.R

internal enum class SettingsScreen(val key: String?, val titleRes: Int) {
    StandardOverview(null, R.string.settings_title),
    ElderOverview(null, R.string.settings_elder_title),
    Contacts("contacts", R.string.settings_contacts_title),
    WeChatRules("wechat_rules", R.string.settings_wechat_rules_title),
    Calls("calls", R.string.settings_calls_title),
    CallDiagnostics("diagnostics", R.string.settings_diagnostics_title),
    Safety("safety", R.string.settings_safety_title),
    Permissions("permissions", R.string.settings_permissions_title),
    Background("background", R.string.settings_background_title),
    Device("device", R.string.settings_device_title),
    Display("display", R.string.settings_display_title),
    Advanced("advanced", R.string.settings_advanced_title),
    Weather("weather", R.string.settings_weather_title),
    System("system", R.string.settings_section_system_title),
    About("about", R.string.settings_about_title);

    companion object {
        fun from(mode: String?, section: String?): SettingsScreen {
            return section?.let { key -> entries.firstOrNull { it.key == key } }
                ?: StandardOverview
        }
    }
}
