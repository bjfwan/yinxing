package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.R

internal fun SettingsActivity.showPermissionGroupsSheet() {
    val sheet = createListSheet(
        title = getString(R.string.settings_section_support_title),
        message = getString(R.string.settings_sheet_permissions_message)
    )
    PermissionGroup.entries.forEach { group ->
        val renderState = overviewController.permissionGroupRenderState(group)
        addSheetEntry(
            context = sheet,
            title = getString(group.titleRes),
            summary = renderState.summary,
            badge = renderState.badge
        ) {
            sheet.dialog.dismiss()
            showPermissionGroupDialog(group)
        }
    }
    sheet.dialog.show()
}

internal fun SettingsActivity.showPermissionGroupDialog(group: PermissionGroup) {
    val sheet = createListSheet(
        title = getString(group.titleRes),
        message = getString(group.dialogMessageRes)
    )
    group.entries.mapNotNull(overviewController.currentPermissionEntryStates()::get).forEach { state ->
        addSheetEntry(
            context = sheet,
            title = overviewController.permissionEntryTitle(state.entry),
            summary = overviewController.permissionEntrySummary(state),
            badge = overviewController.permissionEntryBadge(state)
        ) {
            sheet.dialog.dismiss()
            actionController.openPermissionEntry(state.entry)
        }
    }
    sheet.dialog.show()
}
