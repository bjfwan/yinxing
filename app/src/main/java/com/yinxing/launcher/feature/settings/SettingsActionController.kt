package com.yinxing.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.feature.incoming.IncomingGuardItem

internal class SettingsActionController(
    private val activity: SettingsActivity
) {
    fun onPhonePermissionResult(results: Map<String, Boolean>) {
        val granted = results.values.all { it }
        if (granted) {
            Toast.makeText(
                activity,
                activity.getString(R.string.settings_phone_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openAppDetailSettings(activity)
        }
        activity.overviewController.refreshOverviewUi()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            Toast.makeText(
                activity,
                activity.getString(R.string.settings_notification_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openNotificationSettings(activity)
        }
        activity.overviewController.refreshOverviewUi()
    }

    fun onDefaultLauncherRoleResult() {
        activity.overviewController.refreshOverviewUi()
        if (activity.isDefaultLauncher()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.set_default_launcher_summary_on),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openPermissionEntry(entry: PermissionEntry) = activity.openPermissionEntry(entry)

    fun openIncomingGuardItem(item: IncomingGuardItem) = activity.openIncomingGuardItem(item)

    fun openSystemSettings() = activity.openSystemSettings()

    fun showSetDefaultLauncherDialog() = activity.showSetDefaultLauncherDialog()

    fun clearDefaultLauncher() = activity.clearDefaultLauncher()

    fun isDefaultLauncher(): Boolean = activity.isDefaultLauncher()
}

internal fun SettingsActivity.openPermissionEntry(entry: PermissionEntry) {
    when (entry) {
        PermissionEntry.PhonePermission -> requestPhonePermissions()
        PermissionEntry.NotificationPermission -> requestNotificationPermission()
        PermissionEntry.DefaultLauncher -> openIncomingGuardItem(IncomingGuardItem.DefaultLauncher)
        PermissionEntry.BatteryOptimization -> openIncomingGuardItem(IncomingGuardItem.BatteryOptimization)
        PermissionEntry.AutoStart -> openIncomingGuardItem(IncomingGuardItem.AutoStart)
        PermissionEntry.BackgroundStart -> openIncomingGuardItem(IncomingGuardItem.BackgroundStart)
        PermissionEntry.Accessibility -> PermissionUtil.openAccessibilitySettings(this)
        PermissionEntry.Overlay -> PermissionUtil.openOverlaySettings(this)
    }
}

internal fun SettingsActivity.openIncomingGuardItem(item: IncomingGuardItem) {
    when (item) {
        IncomingGuardItem.PhonePermission -> requestPhonePermissions()
        IncomingGuardItem.NotificationPermission -> requestNotificationPermission()
        IncomingGuardItem.DefaultLauncher -> {
            if (isDefaultLauncher()) {
                Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
            } else {
                showSetDefaultLauncherDialog()
            }
        }
        IncomingGuardItem.BatteryOptimization -> PermissionUtil.openBatteryOptimizationSettings(this)
        IncomingGuardItem.AutoStart -> showManualCheckDialog(IncomingGuardItem.AutoStart)
        IncomingGuardItem.BackgroundStart -> showManualCheckDialog(IncomingGuardItem.BackgroundStart)
    }
}

internal fun SettingsActivity.requestPhonePermissions() {
    if (PermissionUtil.hasPhonePermission(this)) {
        Toast.makeText(
            this,
            getString(R.string.settings_phone_permission_granted_toast),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val permissions = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }
    phonePermissionLauncher.launch(permissions.toTypedArray())
}

internal fun SettingsActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !PermissionUtil.hasNotificationPermission(this)
    ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        PermissionUtil.openNotificationSettings(this)
    }
}

internal fun SettingsActivity.showManualCheckDialog(item: IncomingGuardItem) {
    val title = overviewController.guardTitle(item)
    val confirmed = when (item) {
        IncomingGuardItem.AutoStart -> launcherPreferences.isAutoStartConfirmed()
        IncomingGuardItem.BackgroundStart -> launcherPreferences.isBackgroundStartConfirmed()
        else -> false
    }
    val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
    dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = title
    dialogView.findViewById<TextView>(R.id.tv_dialog_message).text =
        getString(R.string.settings_manual_check_message, title)
    dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(
        if (confirmed) R.string.settings_manual_check_mark_pending
        else R.string.settings_manual_check_mark_done
    )
    dialogView.findViewById<TextView>(R.id.tv_primary_label).text =
        getString(R.string.action_go_to_settings)

    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
        dialog.dismiss()
        setManualGuardConfirmation(item, !confirmed)
    }
    dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
        dialog.dismiss()
        when (item) {
            IncomingGuardItem.AutoStart -> PermissionUtil.openAutoStartSettings(this)
            IncomingGuardItem.BackgroundStart -> PermissionUtil.openBackgroundStartSettings(this)
            else -> Unit
        }
    }
    dialog.show()
}

internal fun SettingsActivity.setManualGuardConfirmation(item: IncomingGuardItem, confirmed: Boolean) {
    when (item) {
        IncomingGuardItem.AutoStart -> launcherPreferences.setAutoStartConfirmed(confirmed)
        IncomingGuardItem.BackgroundStart -> launcherPreferences.setBackgroundStartConfirmed(confirmed)
        else -> return
    }
    Toast.makeText(
        this,
        getString(
            if (confirmed) R.string.settings_manual_check_done_toast
            else R.string.settings_manual_check_reset_toast,
            overviewController.guardTitle(item)
        ),
        Toast.LENGTH_SHORT
    ).show()
    overviewController.refreshOverviewUi()
}

internal fun SettingsActivity.openSystemSettings() {
    try {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
    }
}

internal fun SettingsActivity.isDefaultLauncher(): Boolean {
    return PermissionUtil.isDefaultLauncher(this)
}

internal fun SettingsActivity.showSetDefaultLauncherDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
    dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = getString(R.string.set_default_launcher_title)
    dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = getString(R.string.set_default_launcher_message)
    dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(R.string.set_default_launcher_later)
    dialogView.findViewById<TextView>(R.id.tv_primary_label).text = getString(R.string.set_default_launcher_action)

    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
        dialog.dismiss()
    }
    dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
        dialog.dismiss()
        requestDefaultLauncherRole()
    }
    dialog.show()
}

internal fun SettingsActivity.clearDefaultLauncher() {
    if (!isDefaultLauncher()) {
        Toast.makeText(this, getString(R.string.clear_default_launcher_summary_off), Toast.LENGTH_SHORT).show()
        return
    }
    try {
        @Suppress("DEPRECATION")
        packageManager.clearPackagePreferredActivities(packageName)
    } catch (_: Exception) {
    }
    openDefaultLauncherSettings()
    Toast.makeText(this, "请在系统设置中选择其他桌面应用", Toast.LENGTH_LONG).show()
}

internal fun SettingsActivity.requestDefaultLauncherRole() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestDefaultLauncherRoleByRoleManager()) {
        return
    }
    openDefaultLauncherSettings()
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun SettingsActivity.requestDefaultLauncherRoleByRoleManager(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java) ?: return false
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        return false
    }
    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
        overviewController.refreshOverviewUi()
        Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
        return true
    }
    return runCatching {
        defaultLauncherRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        true
    }.getOrDefault(false)
}

internal fun SettingsActivity.openDefaultLauncherSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: Exception) {
            Log.w("SettingsActivity", "openDefaultLauncherSettings failed for ${intent.action}")
        }
    }
    Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
}
