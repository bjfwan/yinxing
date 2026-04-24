package com.yinxing.launcher.feature.videocall

import android.net.Uri
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.Contact
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class VideoContactDialogController(
    private val activity: AppCompatActivity,
    private val onPickPhoto: () -> Unit,
    private val onSaveContact: (Contact?, String, String, String?) -> Unit,
    private val onDeleteContact: (Contact) -> Unit,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onOpenOverlaySettings: () -> Unit,
    private val onContinueWithoutOverlayPermission: (Contact) -> Unit
) {
    private var selectedAvatarUri: String? = null
    private var photoPreview: ImageView? = null
    private var previewJob: Job? = null


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
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_delete_contact, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_delete_message).text =
            activity.getString(R.string.video_contact_delete_message, contact.displayName)
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_delete)
            .setOnClickListener {
                onDeleteContact(contact)
                dialog.dismiss()
            }
        dialog.show()
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
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_overlay_permission, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_go_to_settings)
            .setOnClickListener {
                onOpenOverlaySettings()
                dialog.dismiss()
            }
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_continue)
            .setOnClickListener {
                onContinueWithoutOverlayPermission(contact)
                dialog.dismiss()
            }
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
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
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)
        selectedAvatarUri = initialContact?.avatarUri

        nameField.setText(initialContact?.displayName.orEmpty())
        wechatField.setText(initialContact?.wechatSearchName.orEmpty())
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
            if (name.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (wechatName.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.contact_wechat_search_name_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSaveContact(initialContact, name, wechatName, selectedAvatarUri)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            previewJob?.cancel()
            photoPreview = null
            selectedAvatarUri = null
        }

        dialog.show()
    }

    private fun renderSelectedPhoto() {
        val preview = photoPreview ?: return
        val avatarUri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        previewJob?.cancel()
        if (avatarUri == null) {
            preview.setImageResource(android.R.drawable.ic_menu_camera)
            preview.setPadding(dp(28), dp(28), dp(28), dp(28))
            return
        }
        preview.setPadding(0, 0, 0, 0)
        preview.setImageDrawable(null)
        previewJob = activity.lifecycleScope.launch {
            val bitmap = MediaThumbnailLoader.loadBitmap(activity, Uri.parse(avatarUri), 480, 480)
            if (photoPreview === preview && selectedAvatarUri == avatarUri && bitmap != null) {
                preview.setImageBitmap(bitmap)
            }
        }
    }


    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
