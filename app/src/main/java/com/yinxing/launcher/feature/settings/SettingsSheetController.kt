package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import com.yinxing.launcher.feature.incoming.IncomingGuardItem
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class SettingsSheetController(
    private val activity: SettingsActivity
) {
    fun showIncomingGuardSheet() = activity.showIncomingGuardSheet()

    fun showContactsSheet() = activity.showContactsSheet()

    fun showAutoAnswerSheet() = activity.showAutoAnswerSheet()

    fun showPermissionGroupsSheet() = activity.showPermissionGroupsSheet()

    fun showDeviceSettingsSheet() = activity.showDeviceSettingsSheet()

    fun showSystemSheet() = activity.showSystemSheet()

    fun playEntryAnimation() = activity.playEntryAnimation()
}

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

internal fun SettingsActivity.showContactsSheet() {
    lifecycleScope.launch {
        val counts = try {
            overviewController.loadContactCounts()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Toast.makeText(
                this@showContactsSheet,
                getString(R.string.contact_load_failed, throwable.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
            ContactCounts(0, 0)
        }
        val sheet = createListSheet(
            title = getString(R.string.settings_section_quick_setup_title),
            message = getString(R.string.settings_sheet_contacts_message)
        )
        val homeAppCount = launcherPreferences.getSelectedPackages().size

        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_phone_contacts_title),
            summary = getString(R.string.settings_contacts_phone_count, counts.phoneCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(PhoneContactActivity.createIntent(this@showContactsSheet, startInManageMode = true))
        }
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_video_contacts_title),
            summary = getString(R.string.settings_contacts_video_count, counts.videoCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(VideoCallActivity.createIntent(this@showContactsSheet, startInManageMode = true))
        }
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_home_apps_title),
            summary = getString(R.string.settings_home_apps_count, homeAppCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(Intent(this@showContactsSheet, AppManageActivity::class.java))
        }
        addSheetTip(
            context = sheet,
            title = getString(R.string.settings_home_apps_tip_title),
            lines = listOf(
                getString(R.string.settings_home_apps_tip_drag),
                getString(R.string.settings_home_apps_tip_add),
                getString(R.string.settings_home_apps_tip_remove)
            )
        )
        sheet.dialog.show()
    }
}

internal fun SettingsActivity.showAutoAnswerSheet() {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_settings_auto_answer, null)
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

internal fun SettingsActivity.showDeviceSettingsSheet() {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_settings_device, null)
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

internal fun SettingsActivity.playEntryAnimation() {
    val root = findViewById<View>(R.id.scroll_settings_root) ?: return
    root.alpha = 0f
    root.translationY = 24f
    root.post {
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
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

internal fun SettingsActivity.showSystemSheet() {
    val sheet = createListSheet(
        title = getString(R.string.settings_section_system_title),
        message = getString(R.string.settings_sheet_system_message)
    )
    addSheetEntry(
        context = sheet,
        title = getString(R.string.settings_weather_city_title),
        summary = getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
        badge = actionBadge(getString(R.string.settings_entry_modify))
    ) {
        sheet.dialog.dismiss()
        showSetCityDialog()
    }
    addSheetEntry(
        context = sheet,
        title = getString(R.string.settings_system_title),
        summary = getString(R.string.settings_system_summary),
        badge = actionBadge(getString(R.string.settings_entry_open_settings))
    ) {
        sheet.dialog.dismiss()
        actionController.openSystemSettings()
    }
    sheet.dialog.show()
}

internal fun SettingsActivity.createListSheet(title: String, message: String): ListSheetContext {
    val dialog = BottomSheetDialog(this)
    val contentView = layoutInflater.inflate(R.layout.dialog_permission_group, null)
    contentView.findViewById<TextView>(R.id.tv_dialog_title).text = title
    contentView.findViewById<TextView>(R.id.tv_dialog_message).text = message
    contentView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
    dialog.setContentView(contentView)
    return ListSheetContext(
        dialog = dialog,
        contentView = contentView,
        container = contentView.findViewById(R.id.layout_permission_items)
    )
}

internal fun SettingsActivity.addSheetEntry(
    context: ListSheetContext,
    title: String,
    summary: String,
    badge: BadgeStyle,
    onClick: () -> Unit
) {
    val itemView = layoutInflater.inflate(R.layout.item_settings_permission_entry, context.container, false)
    itemView.findViewById<TextView>(R.id.tv_permission_item_title).text = title
    itemView.findViewById<TextView>(R.id.tv_permission_item_summary).text = summary
    overviewController.applyInfoBadge(
        tv = itemView.findViewById(R.id.tv_permission_item_status),
        text = badge.text,
        textColorResId = badge.textColorResId,
        backgroundColorResId = badge.backgroundColorResId
    )
    itemView.setOnClickListener { onClick() }
    context.container.addView(itemView)
}

internal fun SettingsActivity.addSheetTip(
    context: ListSheetContext,
    title: String,
    lines: List<String>
) {
    val tipView = layoutInflater.inflate(R.layout.item_settings_tip, context.container, false)
    tipView.findViewById<TextView>(R.id.tv_tip_title).text = title
    val linesContainer = tipView.findViewById<LinearLayout>(R.id.layout_tip_lines)
    lines.forEachIndexed { index, text ->
        val lineView = layoutInflater.inflate(R.layout.item_settings_tip_line, linesContainer, false)
        lineView.findViewById<TextView>(R.id.tv_tip_line_index).text = "${index + 1}."
        lineView.findViewById<TextView>(R.id.tv_tip_line_text).text = text
        linesContainer.addView(lineView)
    }
    context.container.addView(tipView)
}

internal fun SettingsActivity.actionBadge(text: String): BadgeStyle {
    return BadgeStyle(
        text = text,
        textColorResId = R.color.launcher_action,
        backgroundColorResId = R.color.launcher_surface_muted
    )
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

internal fun SettingsActivity.showSetCityDialog() {
    val currentCity = weatherPreferences.getCityName()
    val dialogView = layoutInflater.inflate(R.layout.dialog_set_city, null)
    val etCity = dialogView.findViewById<EditText>(R.id.et_city)
    etCity.setText(currentCity)
    etCity.setSelection(currentCity.length)

    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_cancel)
        .setOnClickListener { dialog.dismiss() }

    val confirm = {
        val city = etCity.text.toString().trim()
        if (city.isNotEmpty()) {
            dialog.dismiss()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etCity.windowToken, 0)
            weatherPreferences.setCityName(city)
            WeatherRepository.clearCache()
            overviewController.updateSystemHubCard()
            Toast.makeText(
                this,
                getString(R.string.settings_weather_city_updated, city),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.settings_weather_city_empty), Toast.LENGTH_SHORT).show()
        }
    }

    dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_confirm)
        .setOnClickListener { confirm() }

    etCity.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            confirm()
            true
        } else {
            false
        }
    }

    dialog.show()
    etCity.postDelayed({
        etCity.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etCity, InputMethodManager.SHOW_IMPLICIT)
    }, 100)
}
