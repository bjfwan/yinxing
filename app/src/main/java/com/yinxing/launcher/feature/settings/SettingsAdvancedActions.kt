package com.yinxing.launcher.feature.settings

import android.content.Intent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.common.diagnostics.LauncherDiagnosticReportCollector
import com.yinxing.launcher.common.diagnostics.LauncherDiagnosticReportFormatter
import com.yinxing.launcher.data.home.LauncherPreferences
import java.io.File

internal fun SettingsActivity.showHomeLongPressDialog(): android.app.Dialog {
    val current = launcherPreferences.getHomeLongPressResponse()
    val options = listOf(
        Pair(
            LauncherPreferences.HOME_LONG_PRESS_STANDARD,
            R.string.settings_home_long_press_standard
        ),
        Pair(
            LauncherPreferences.HOME_LONG_PRESS_LONG,
            R.string.settings_home_long_press_long
        )
    )
    val dialog = createChoiceDialog(
        title = getString(R.string.settings_home_long_press_title),
        message = getString(R.string.settings_home_long_press_dialog_message)
    )
    options.forEach { (value, titleRes) ->
        val isCurrent = value == current
        addDialogChoice(
            context = dialog,
            title = getString(titleRes),
            badge = BadgeStyle(
                text = getString(
                    if (isCurrent) R.string.status_current else R.string.settings_dark_mode_select
                ),
                textColorResId = if (isCurrent) {
                    R.color.launcher_action_dark
                } else {
                    R.color.launcher_text_secondary
                },
                backgroundColorResId = if (isCurrent) {
                    R.color.launcher_call_soft
                } else {
                    R.color.launcher_surface_soft
                }
            )
        ) {
            launcherPreferences.setHomeLongPressResponse(value)
            dialog.dialog.dismiss()
            detailController.bind(SettingsScreen.Advanced)
        }
    }
    dialog.dialog.show()
    return dialog.dialog
}

internal fun SettingsActivity.showResetHomeLayoutDialog(): AlertDialog {
    val view = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, FrameLayout(this), false)
    view.findViewById<TextView>(R.id.tv_dialog_title).setText(R.string.settings_home_layout_reset_title)
    view.findViewById<TextView>(R.id.tv_dialog_message).setText(R.string.settings_home_layout_reset_message)
    view.findViewById<TextView>(R.id.tv_cancel_label).setText(R.string.action_cancel)
    view.findViewById<TextView>(R.id.tv_primary_label).setText(R.string.settings_action_restore)
    val dialog = LauncherDialogFactory.create(this, view, dismissOnTouchOutside = false)
    view.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
    view.findViewById<android.view.View>(R.id.btn_open_settings).setOnClickListener {
        launcherPreferences.resetHomeLayout()
        dialog.dismiss()
        Toast.makeText(this, R.string.settings_home_layout_reset_done, Toast.LENGTH_SHORT).show()
    }
    dialog.show()
    return dialog
}

internal fun SettingsActivity.exportDiagnosticInfo() {
    runCatching {
        val report = LauncherDiagnosticReportFormatter.format(
            LauncherDiagnosticReportCollector.collect(this)
        )
        val directory = File(cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "yinxing-diagnostics.txt").apply {
            writeText(report, Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_diagnostic_export_subject))
            putExtra(Intent.EXTRA_TEXT, report)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_diagnostic_export_chooser)))
    }.onFailure {
        Toast.makeText(this, R.string.settings_diagnostic_export_failed, Toast.LENGTH_SHORT).show()
    }
}
