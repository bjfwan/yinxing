package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.incoming.IncomingGuardItem

internal enum class PermissionGroup(
    val titleRes: Int,
    val readySummaryRes: Int,
    val dialogMessageRes: Int
) {
    Call(
        R.string.settings_permission_group_call_title,
        R.string.settings_permission_group_call_summary,
        R.string.settings_permission_group_call_dialog_message
    ),
    KeepAlive(
        R.string.settings_permission_group_keep_alive_title,
        R.string.settings_permission_group_keep_alive_summary,
        R.string.settings_permission_group_keep_alive_dialog_message
    ),
    Video(
        R.string.settings_permission_group_video_title,
        R.string.settings_permission_group_video_summary,
        R.string.settings_permission_group_video_dialog_message
    );

    val entries: List<PermissionEntry>
        get() = when (this) {
            Call -> listOf(
                PermissionEntry.PhonePermission,
                PermissionEntry.NotificationPermission
            )
            KeepAlive -> listOf(
                PermissionEntry.DefaultLauncher,
                PermissionEntry.BatteryOptimization,
                PermissionEntry.AutoStart,
                PermissionEntry.BackgroundStart
            )
            Video -> listOf(
                PermissionEntry.Accessibility,
                PermissionEntry.Overlay
            )
        }
}

internal enum class PermissionEntry {
    PhonePermission,
    NotificationPermission,
    DefaultLauncher,
    BatteryOptimization,
    AutoStart,
    BackgroundStart,
    Accessibility,
    Overlay
}

internal data class PermissionEntryState(
    val entry: PermissionEntry,
    val isReady: Boolean,
    val requiresManualConfirmation: Boolean = false
)

internal data class BadgeStyle(
    val text: String,
    val textColorResId: Int,
    val backgroundColorResId: Int
)

internal data class ListSheetContext(
    val dialog: BottomSheetDialog,
    val contentView: View,
    val container: LinearLayout
)

internal data class GroupRenderState(
    val summary: String,
    val badge: BadgeStyle
)

internal data class ContactCounts(
    val phoneCount: Int,
    val videoCount: Int
)

internal fun IncomingGuardItem.toPermissionEntry(): PermissionEntry {
    return when (this) {
        IncomingGuardItem.PhonePermission -> PermissionEntry.PhonePermission
        IncomingGuardItem.NotificationPermission -> PermissionEntry.NotificationPermission
        IncomingGuardItem.DefaultLauncher -> PermissionEntry.DefaultLauncher
        IncomingGuardItem.BatteryOptimization -> PermissionEntry.BatteryOptimization
        IncomingGuardItem.AutoStart -> PermissionEntry.AutoStart
        IncomingGuardItem.BackgroundStart -> PermissionEntry.BackgroundStart
    }
}
