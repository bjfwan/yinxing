package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.R

internal fun SettingsActivity.showIncomingGuardSheet() {
    val readiness = overviewController.currentIncomingGuardReadiness()
    val blocker = readiness.blocker?.item
    val message = if (readiness.isReady) {
        getString(R.string.settings_incoming_guard_summary_ready)
    } else {
        getString(
            R.string.settings_incoming_guard_summary_blocked,
            blocker?.let(overviewController::guardTitle).orEmpty()
        )
    }
    val sheet = createListSheet(
        title = getString(R.string.settings_section_incoming_guard_title),
        message = message
    )

    readiness.items.forEach { itemState ->
        val entry = itemState.item.toPermissionEntry()
        val state = overviewController.currentPermissionEntryStates()[entry] ?: return@forEach
        val badge = when {
            itemState.isReady && itemState.requiresManualConfirmation -> BadgeStyle(
                text = getString(R.string.settings_guard_status_confirmed),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            itemState.isReady -> BadgeStyle(
                text = getString(R.string.settings_guard_status_done),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            readiness.blocker?.item == itemState.item -> BadgeStyle(
                text = getString(R.string.settings_guard_status_blocking),
                textColorResId = R.color.launcher_warning,
                backgroundColorResId = R.color.launcher_warning_soft
            )
            else -> overviewController.permissionEntryBadge(state)
        }
        addSheetEntry(
            context = sheet,
            title = overviewController.guardTitle(itemState.item),
            summary = overviewController.permissionEntrySummary(state),
            badge = badge
        ) {
            sheet.dialog.dismiss()
            actionController.openIncomingGuardItem(itemState.item)
        }
    }
    sheet.dialog.show()
}
