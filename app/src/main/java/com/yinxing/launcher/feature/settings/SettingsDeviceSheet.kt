package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences

internal fun SettingsActivity.showDeviceSettingsSheet() {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_settings_device, FrameLayout(this), false)
    dialog.setContentView(view)

    val defaultLauncherSummary = view.findViewById<TextView>(R.id.tv_default_launcher_sheet_summary)
    val clearDefaultLauncherSummary = view.findViewById<TextView>(R.id.tv_clear_default_launcher_sheet_summary)
    val kioskModeSwitch = view.findViewById<SwitchCompat>(R.id.switch_kiosk_mode_sheet)
    val kioskModeSummary = view.findViewById<TextView>(R.id.tv_kiosk_mode_sheet_summary)
    val lowPerformanceSwitch = view.findViewById<SwitchCompat>(R.id.switch_low_performance_sheet)
    val lowPerformanceSummary = view.findViewById<TextView>(R.id.tv_low_performance_sheet_summary)
    val iconScaleSeekBar = view.findViewById<android.widget.SeekBar>(R.id.seekbar_icon_scale)
    val iconScaleValue = view.findViewById<TextView>(R.id.tv_icon_scale_value)
    val darkModeSummary = view.findViewById<TextView>(R.id.tv_dark_mode_summary)
    val darkModeSystem = view.findViewById<MaterialCardView>(R.id.btn_dark_mode_system)
    val darkModeLight = view.findViewById<MaterialCardView>(R.id.btn_dark_mode_light)
    val darkModeDark = view.findViewById<MaterialCardView>(R.id.btn_dark_mode_dark)

    iconScaleSeekBar.progress = launcherPreferences.getIconScale() - LauncherPreferences.MIN_ICON_SCALE
    iconScaleValue.text = getString(R.string.settings_icon_scale_summary, launcherPreferences.getIconScale())
    iconScaleSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
            val scale = progress + LauncherPreferences.MIN_ICON_SCALE
            iconScaleValue.text = getString(R.string.settings_icon_scale_summary, scale)
        }
        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
            val scale = (seekBar?.progress ?: 0) + LauncherPreferences.MIN_ICON_SCALE
            launcherPreferences.setIconScale(scale)
        }
    })

    fun updateSheetState() {
        val isDefault = actionController.isDefaultLauncher()
        defaultLauncherSummary.text = getString(
            if (isDefault) R.string.set_default_launcher_summary_on
            else R.string.set_default_launcher_summary_off
        )
        clearDefaultLauncherSummary.text = getString(
            if (isDefault) R.string.clear_default_launcher_summary_on
            else R.string.clear_default_launcher_summary_off
        )
        kioskModeSwitch.isChecked = launcherPreferences.isKioskModeEnabled()
        kioskModeSummary.text = getString(
            if (launcherPreferences.isKioskModeEnabled()) R.string.settings_kiosk_mode_summary_on
            else R.string.settings_kiosk_mode_summary_off
        )
        lowPerformanceSwitch.isChecked = launcherPreferences.isLowPerformanceModeEnabled()
        lowPerformanceSummary.text = getString(
            if (launcherPreferences.isLowPerformanceModeEnabled()) R.string.settings_low_performance_summary_on
            else R.string.settings_low_performance_summary_off
        )
        applyDarkModeChips(
            launcherPreferences.getDarkMode(),
            darkModeSummary,
            darkModeSystem,
            darkModeLight,
            darkModeDark
        )
    }

    updateSheetState()

    val darkModeChips = mapOf(
        LauncherPreferences.DARK_MODE_SYSTEM to darkModeSystem,
        LauncherPreferences.DARK_MODE_LIGHT to darkModeLight,
        LauncherPreferences.DARK_MODE_DARK to darkModeDark
    )
    darkModeChips.forEach { (mode, chip) ->
        chip.setOnClickListener {
            playChipTapPulse(chip)
            handleDarkModeChipPicked(mode, dialog)
        }
    }

    view.findViewById<View>(R.id.btn_set_default_launcher_sheet).setOnClickListener {
        dialog.dismiss()
        if (actionController.isDefaultLauncher()) {
            Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
        } else {
            actionController.showSetDefaultLauncherDialog()
        }
    }

    view.findViewById<View>(R.id.btn_clear_default_launcher_sheet).setOnClickListener {
        dialog.dismiss()
        actionController.clearDefaultLauncher()
    }

    kioskModeSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !actionController.isDefaultLauncher()) {
            kioskModeSwitch.isChecked = false
            Toast.makeText(
                this,
                getString(R.string.settings_kiosk_mode_requires_default_launcher),
                Toast.LENGTH_LONG
            ).show()
            return@setOnCheckedChangeListener
        }
        launcherPreferences.setKioskModeEnabled(isChecked)
        updateSheetState()
        overviewController.refreshDeviceHubCard()
    }
    lowPerformanceSwitch.setOnCheckedChangeListener { _, isChecked ->
        launcherPreferences.setLowPerformanceModeEnabled(isChecked)
        updateSheetState()
        overviewController.refreshDeviceHubCard()
    }
    view.findViewById<View>(R.id.btn_keep_alive_review_sheet).setOnClickListener {
        dialog.dismiss()
        showPermissionGroupDialog(PermissionGroup.KeepAlive)
    }
    view.findViewById<View>(R.id.btn_close_device_sheet).setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}

internal fun SettingsActivity.animateChipState(
    chip: MaterialCardView,
    targetStrokeColor: Int,
    targetFillColor: Int,
    targetStrokeWidth: Int
) {
    val fromStroke = chip.strokeColorStateList?.defaultColor ?: targetStrokeColor
    val fromFill = chip.cardBackgroundColor.defaultColor
    if (fromStroke == targetStrokeColor && fromFill == targetFillColor && chip.strokeWidth == targetStrokeWidth) {
        return
    }
    chip.strokeWidth = targetStrokeWidth
    val tag = chip.getTag(R.id.tag_chip_color_animator)
    (tag as? android.animation.ValueAnimator)?.cancel()
    val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 180
        interpolator = android.view.animation.DecelerateInterpolator()
        addUpdateListener { anim ->
            val t = anim.animatedFraction
            chip.strokeColor = androidx.core.graphics.ColorUtils.blendARGB(fromStroke, targetStrokeColor, t)
            chip.setCardBackgroundColor(
                androidx.core.graphics.ColorUtils.blendARGB(fromFill, targetFillColor, t)
            )
        }
    }
    chip.setTag(R.id.tag_chip_color_animator, animator)
    animator.start()
}

internal fun SettingsActivity.playChipTapPulse(chip: View) {
    chip.animate().cancel()
    chip.scaleX = 1f
    chip.scaleY = 1f
    chip.animate()
        .scaleX(0.96f)
        .scaleY(0.96f)
        .setDuration(80)
        .withEndAction {
            chip.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                .start()
        }
        .start()
}

internal fun SettingsActivity.handleDarkModeChipPicked(mode: String, sheetDialog: BottomSheetDialog) {
    val newNightMode = when (mode) {
        LauncherPreferences.DARK_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        LauncherPreferences.DARK_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    val previous = launcherPreferences.getDarkMode()
    launcherPreferences.setDarkMode(mode)
    if (previous == mode && AppCompatDelegate.getDefaultNightMode() == newNightMode) {
        return
    }
    sheetDialog.dismiss()
    val root = findViewById<View>(R.id.scroll_settings_root) ?: window.decorView
    root.animate()
        .alpha(0f)
        .setDuration(160)
        .setInterpolator(android.view.animation.AccelerateInterpolator())
        .withEndAction {
            AppCompatDelegate.setDefaultNightMode(newNightMode)
        }
        .start()
}

internal fun SettingsActivity.applyDarkModeChips(
    current: String,
    summary: TextView,
    systemChip: MaterialCardView,
    lightChip: MaterialCardView,
    darkChip: MaterialCardView
) {
    val activeStroke = ContextCompat.getColor(this, R.color.launcher_action)
    val activeFill = ContextCompat.getColor(this, R.color.launcher_primary_soft)
    val inactiveStroke = ContextCompat.getColor(this, R.color.launcher_outline)
    val inactiveFill = ContextCompat.getColor(this, R.color.launcher_surface)
    val density = resources.displayMetrics.density
    val activeStrokeWidth = (2f * density).toInt()
    val inactiveStrokeWidth = (1f * density).toInt()
    listOf(
        systemChip to LauncherPreferences.DARK_MODE_SYSTEM,
        lightChip to LauncherPreferences.DARK_MODE_LIGHT,
        darkChip to LauncherPreferences.DARK_MODE_DARK
    ).forEach { (chip, mode) ->
        val active = mode == current
        animateChipState(
            chip = chip,
            targetStrokeColor = if (active) activeStroke else inactiveStroke,
            targetFillColor = if (active) activeFill else inactiveFill,
            targetStrokeWidth = if (active) activeStrokeWidth else inactiveStrokeWidth
        )
    }
    summary.text = getString(
        when (current) {
            LauncherPreferences.DARK_MODE_LIGHT -> R.string.settings_dark_mode_summary_light
            LauncherPreferences.DARK_MODE_DARK -> R.string.settings_dark_mode_summary_dark
            else -> R.string.settings_dark_mode_summary_system
        }
    )
}
