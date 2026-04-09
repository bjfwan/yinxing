package com.bajianfeng.launcher.feature.videocall

import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact

class VideoContactDialogController(
    private val activity: AppCompatActivity,
    private val onAddContact: (String) -> Unit,
    private val onDeleteContact: (Contact) -> Unit,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onOpenOverlaySettings: () -> Unit,
    private val onContinueWithoutOverlayPermission: (Contact) -> Unit
) {
    fun showAddContactDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val nameField = dialogView.findViewById<EditText>(R.id.et_contact_name)

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = nameField.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onAddContact(name)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.delete_contact_title)
            .setMessage(activity.getString(R.string.video_contact_delete_message, contact.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                onDeleteContact(contact)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showAccessibilityDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<CardView>(R.id.btn_open_settings).setOnClickListener {
            onOpenAccessibilitySettings()
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showOverlayPermissionDialog(contact: Contact) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.action_go_to_settings) { _, _ ->
                onOpenOverlaySettings()
            }
            .setNegativeButton(R.string.action_continue) { _, _ ->
                onContinueWithoutOverlayPermission(contact)
            }
            .setNeutralButton(R.string.action_cancel, null)
            .show()
    }
}
