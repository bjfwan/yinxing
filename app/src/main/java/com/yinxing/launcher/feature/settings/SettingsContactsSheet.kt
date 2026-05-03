package com.yinxing.launcher.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
