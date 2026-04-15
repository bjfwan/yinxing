package com.bajianfeng.launcher.feature.videocall

import android.net.Uri
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact

class VideoContactDialogController(
    private val activity: AppCompatActivity,
    private val onPickPhoto: () -> Unit,
    private val onSaveContact: (Contact?, String, String, String, Contact.PreferredAction, String?) -> Unit,
    private val onDeleteContact: (Contact) -> Unit,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onOpenOverlaySettings: () -> Unit,
    private val onContinueWithoutOverlayPermission: (Contact) -> Unit
) {
    private var selectedAvatarUri: String? = null
    private var photoPreview: ImageView? = null

    fun updateSelectedPhoto(uri: Uri) {
        selectedAvatarUri = uri.toString()
        renderSelectedPhoto()
    }

    fun showAddContactDialog() {
        showEditorDialog(null)
    }

    fun showEditContactDialog(contact: Contact) {
        showEditorDialog(contact)
    }

    fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.delete_contact_title)
            .setMessage(activity.getString(R.string.video_contact_delete_message, contact.displayName))

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

    private fun showEditorDialog(initialContact: Contact?) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = activity.getString(
            if (initialContact == null) R.string.contact_dialog_add_title else R.string.contact_dialog_edit_title
        )

        val nameField = dialogView.findViewById<EditText>(R.id.et_contact_name)
        val wechatField = dialogView.findViewById<EditText>(R.id.et_wechat_name)
        val phoneField = dialogView.findViewById<EditText>(R.id.et_phone)
        val actionGroup = dialogView.findViewById<RadioGroup>(R.id.rg_action)
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)
        selectedAvatarUri = initialContact?.avatarUri

        nameField.setText(initialContact?.displayName.orEmpty())
        wechatField.setText(initialContact?.wechatSearchName.orEmpty())

        phoneField.setText(initialContact?.phoneNumber.orEmpty())
        actionGroup.check(
            if ((initialContact?.preferredAction ?: Contact.PreferredAction.PHONE) == Contact.PreferredAction.WECHAT_VIDEO) {
                R.id.rb_action_wechat
            } else {
                R.id.rb_action_phone
            }
        )
        renderSelectedPhoto()

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            onPickPhoto()
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = nameField.text.toString().trim()
            val wechatName = wechatField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            val preferredAction = if (actionGroup.checkedRadioButtonId == R.id.rb_action_wechat) {
                Contact.PreferredAction.WECHAT_VIDEO
            } else {
                Contact.PreferredAction.PHONE
            }
            if (name.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (preferredAction == Contact.PreferredAction.PHONE && phone.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.contact_phone_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (preferredAction == Contact.PreferredAction.WECHAT_VIDEO && wechatName.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.contact_wechat_search_name_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSaveContact(initialContact, name, phone, wechatName, preferredAction, selectedAvatarUri)

            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            photoPreview = null
            selectedAvatarUri = null
        }
        dialog.show()
    }

    private fun renderSelectedPhoto() {
        val preview = photoPreview ?: return
        val avatarUri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        if (avatarUri == null) {
            preview.setImageResource(android.R.drawable.ic_menu_camera)
            preview.setPadding(dp(28), dp(28), dp(28), dp(28))
            return
        }
        preview.setPadding(0, 0, 0, 0)
        preview.setImageURI(null)
        preview.setImageURI(Uri.parse(avatarUri))
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
