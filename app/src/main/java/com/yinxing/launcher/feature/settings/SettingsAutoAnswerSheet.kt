package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics

internal fun SettingsActivity.showAutoAnswerSheet() {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_settings_auto_answer, FrameLayout(this), false)
    dialog.setContentView(view)

    val autoAnswerSwitch = view.findViewById<SwitchCompat>(R.id.switch_auto_answer_sheet)
    val autoAnswerSummary = view.findViewById<TextView>(R.id.tv_auto_answer_sheet_summary)
    val autoAnswerDelaySummary = view.findViewById<TextView>(R.id.tv_auto_answer_delay_sheet_summary)
    val autoAnswerDelayMinus = view.findViewById<View>(R.id.btn_auto_answer_delay_sheet_minus)
    val autoAnswerDelayPlus = view.findViewById<View>(R.id.btn_auto_answer_delay_sheet_plus)
    val fullCardTapSwitch = view.findViewById<SwitchCompat>(R.id.switch_full_card_tap_sheet)
    val fullCardTapSummary = view.findViewById<TextView>(R.id.tv_full_card_tap_sheet_summary)
    val incomingTraceSummary = view.findViewById<TextView>(R.id.tv_incoming_trace_sheet_summary)

    fun updateDelayControls(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.38f
        listOf(autoAnswerDelayMinus, autoAnswerDelayPlus, autoAnswerDelaySummary).forEach { target ->
            target.isEnabled = enabled
            target.alpha = alpha
        }
    }

    fun updateSheetSummary() {
        val enabled = launcherPreferences.isAutoAnswerEnabled()
        autoAnswerSwitch.isChecked = enabled
        autoAnswerSummary.text = getString(
            if (enabled) R.string.settings_auto_answer_summary_on
            else R.string.settings_auto_answer_summary_off
        )
        autoAnswerDelaySummary.text = getString(
            R.string.settings_auto_answer_delay_summary,
            launcherPreferences.getAutoAnswerDelaySeconds()
        )
        val fullCardTap = launcherPreferences.isFullCardTapEnabled()
        fullCardTapSwitch.isChecked = fullCardTap
        fullCardTapSummary.text = getString(
            if (fullCardTap) R.string.settings_full_card_tap_summary_on
            else R.string.settings_full_card_tap_summary_off
        )
        incomingTraceSummary.text = IncomingCallDiagnostics.getDisplayText(this)
        updateDelayControls(enabled)
    }

    updateSheetSummary()

    autoAnswerSwitch.setOnCheckedChangeListener { _, isChecked ->
        launcherPreferences.setAutoAnswerEnabled(isChecked)
        updateSheetSummary()
        overviewController.updateAutoAnswerHubCard()
    }
    autoAnswerDelayMinus.setOnClickListener {
        if (!launcherPreferences.isAutoAnswerEnabled()) return@setOnClickListener
        val updated = launcherPreferences.getAutoAnswerDelaySeconds() - 1
        launcherPreferences.setAutoAnswerDelaySeconds(updated)
        updateSheetSummary()
        overviewController.updateAutoAnswerHubCard()
    }
    autoAnswerDelayPlus.setOnClickListener {
        if (!launcherPreferences.isAutoAnswerEnabled()) return@setOnClickListener
        val updated = launcherPreferences.getAutoAnswerDelaySeconds() + 1
        launcherPreferences.setAutoAnswerDelaySeconds(updated)
        updateSheetSummary()
        overviewController.updateAutoAnswerHubCard()
    }
    fullCardTapSwitch.setOnCheckedChangeListener { _, isChecked ->
        launcherPreferences.setFullCardTapEnabled(isChecked)
        updateSheetSummary()
    }
    view.findViewById<View>(R.id.btn_close_auto_answer_sheet).setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}
