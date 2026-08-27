package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.R

internal fun SettingsActivity.showPermissionGroupDialog(group: PermissionGroup) {
    val dialog = createListDialog(
        title = getString(group.titleRes),
        message = getString(group.dialogMessageRes)
    )
    group.entries.mapNotNull(overviewController.currentPermissionEntryStates()::get).forEach { state ->
        val icon = permissionEntryIcon(state.entry)
        addDialogEntry(
            context = dialog,
            title = overviewController.permissionEntryTitle(state.entry),
            summary = overviewController.permissionEntrySummary(state),
            badge = overviewController.permissionEntryBadge(state),
            iconResId = icon.drawableResId,
            iconTintResId = icon.tintResId,
            iconPlateResId = icon.plateResId
        ) {
            dialog.dialog.dismiss()
            actionController.openPermissionEntry(state.entry)
        }
    }
    dialog.dialog.show()
}

internal fun permissionEntryIcon(entry: PermissionEntry): DialogEntryIcon = when (entry) {
    PermissionEntry.DefaultPhone -> DialogEntryIcon(
        R.drawable.ic_settings_category_calls,
        R.color.launcher_call,
        R.color.launcher_call_soft
    )
    PermissionEntry.PhonePermission -> DialogEntryIcon(
        R.drawable.ic_settings_category_calls,
        R.color.launcher_call,
        R.color.launcher_call_soft
    )
    PermissionEntry.NotificationPermission -> DialogEntryIcon(
        R.drawable.ic_settings_permission_notification,
        R.color.launcher_warning,
        R.color.launcher_warning_soft
    )
    PermissionEntry.DefaultLauncher -> DialogEntryIcon(
        R.drawable.ic_settings_category_device,
        R.color.launcher_contacts,
        R.color.launcher_contacts_soft
    )
    PermissionEntry.BatteryOptimization -> DialogEntryIcon(
        R.drawable.ic_settings_permission_battery,
        R.color.launcher_action,
        R.color.launcher_primary_soft
    )
    PermissionEntry.AutoStart -> DialogEntryIcon(
        R.drawable.ic_settings_action_update,
        R.color.launcher_call,
        R.color.launcher_call_soft
    )
    PermissionEntry.BackgroundStart -> DialogEntryIcon(
        R.drawable.ic_settings_permission_background,
        R.color.launcher_system,
        R.color.launcher_system_soft
    )
    PermissionEntry.Accessibility -> DialogEntryIcon(
        R.drawable.ic_settings_permission_accessibility,
        R.color.launcher_contacts,
        R.color.launcher_contacts_soft
    )
    PermissionEntry.Overlay -> DialogEntryIcon(
        R.drawable.ic_settings_permission_overlay,
        R.color.launcher_warning,
        R.color.launcher_warning_soft
    )
}
