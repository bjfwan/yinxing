package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.data.weather.WeatherRepository
import kotlinx.coroutines.launch

internal fun SettingsActivity.showSystemDialog() {
    val dialog = createListDialog(
        title = getString(R.string.settings_section_system_title),
        message = getString(R.string.settings_dialog_system_message)
    )
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_weather_city_title),
        summary = getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
        badge = actionBadge(getString(R.string.settings_entry_modify)),
        iconResId = R.drawable.ic_weather_sun,
        iconTintResId = R.color.launcher_warning,
        iconPlateResId = R.color.launcher_warning_soft
    ) {
        dialog.dialog.dismiss()
        showSetCityDialog()
    }
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_system_title),
        summary = getString(R.string.settings_system_summary),
        badge = actionBadge(getString(R.string.settings_entry_open_settings)),
        iconResId = R.drawable.ic_settings_category_system,
        iconTintResId = R.color.launcher_system,
        iconPlateResId = R.color.launcher_system_soft
    ) {
        dialog.dialog.dismiss()
        actionController.openSystemSettings()
    }
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_update_title),
        summary = getString(R.string.settings_update_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        badge = actionBadge(getString(R.string.settings_update_check)),
        iconResId = R.drawable.ic_settings_action_update,
        iconTintResId = R.color.launcher_contacts,
        iconPlateResId = R.color.launcher_contacts_soft
    ) {
        dialog.dialog.dismiss()
        checkAppUpdate()
    }
    dialog.dialog.show()
}

internal fun SettingsActivity.checkAppUpdate() {
    Toast.makeText(this, getString(R.string.settings_update_checking), Toast.LENGTH_SHORT).show()
    lifecycleScope.launch {
        when (val state = AppUpdateChecker().check()) {
            AppUpdateState.UpToDate -> {
                Toast.makeText(
                    this@checkAppUpdate,
                    getString(R.string.settings_update_latest),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is AppUpdateState.Available -> showUpdateDialog(state.info)
            is AppUpdateState.Failed -> {
                Toast.makeText(
                    this@checkAppUpdate,
                    getString(R.string.settings_update_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

internal fun SettingsActivity.showUpdateDialog(info: AppUpdateInfo) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, FrameLayout(this), false)
    dialogView.findViewById<TextView>(R.id.tv_dialog_title).text =
        getString(R.string.settings_update_available_title, info.versionName)
    dialogView.findViewById<TextView>(R.id.tv_dialog_message).text =
        info.releaseNotes.ifBlank { getString(R.string.settings_update_available_message) }
    dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(R.string.action_cancel)
    dialogView.findViewById<TextView>(R.id.tv_primary_label).text =
        getString(R.string.settings_update_download)

    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener {
        dialog.dismiss()
    }
    dialogView.findViewById<android.view.View>(R.id.btn_open_settings).setOnClickListener {
        dialog.dismiss()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.settings_update_open_failed), Toast.LENGTH_SHORT).show()
        }
    }
    dialog.show()
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
