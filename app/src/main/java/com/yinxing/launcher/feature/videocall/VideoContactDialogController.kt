package com.yinxing.launcher.feature.videocall

import android.net.Uri
import android.graphics.Bitmap
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
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


    fun updateSelectedPhoto(uri: Uri, bitmap: Bitmap) {
        selectedAvatarUri = uri.toString()
        previewJob?.cancel()
        photoPreview?.apply {
            imageTintList = null
            clearColorFilter()
            setPadding(0, 0, 0, 0)
            setImageBitmap(bitmap)
        }
    }

    fun showAddContactDialog() {
        showEditorDialog(null)
    }

    fun showEditContactDialog(contact: Contact) {
        showEditorDialog(contact)
    }

    fun showDeleteDialog(contact: Contact) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_delete_contact, null)
        val dialog = LauncherDialogFactory.create(activity, dialogView, dismissOnTouchOutside = false)
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
        val dialog = LauncherDialogFactory.create(activity, dialogView, dismissOnTouchOutside = false)
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
        val dialog = LauncherDialogFactory.create(activity, dialogView)
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
        val dialog = LauncherDialogFactory.create(activity, dialogView, dismissOnTouchOutside = false)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = activity.getString(
            if (initialContact == null) R.string.contact_dialog_add_title else R.string.contact_dialog_edit_title
        )

        val nameField = dialogView.findViewById<EditText>(R.id.et_contact_name)
        val wechatField = dialogView.findViewById<EditText>(R.id.et_wechat_name)
        val cancelButton = dialogView.findViewById<MaterialCardView>(R.id.btn_cancel)
        val cancelLabel = dialogView.findViewById<TextView>(R.id.btn_cancel_label)
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)
        selectedAvatarUri = initialContact?.avatarUri

        nameField.setText(initialContact?.displayName.orEmpty())
        wechatField.setText(initialContact?.wechatSearchName.orEmpty())
        renderSelectedPhoto()

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            onPickPhoto()
        }
        if (initialContact != null) {
            cancelLabel.text = activity.getString(R.string.action_delete)
            cancelLabel.setTextColor(ContextCompat.getColor(activity, R.color.launcher_danger))
            cancelButton.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.launcher_danger_soft))
        }
        cancelButton.setOnClickListener {
            dialog.dismiss()
            if (initialContact != null) showDeleteDialog(initialContact)
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
        preview.imageTintList = null
        val avatarUri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        previewJob?.cancel()
        if (avatarUri == null) {
            preview.setImageResource(R.drawable.ic_contact_avatar_placeholder)
            preview.clearColorFilter()
            preview.setPadding(dp(28), dp(28), dp(28), dp(28))
            return
        }
        preview.setImageResource(R.drawable.ic_contact_avatar_placeholder)
        preview.clearColorFilter()
        preview.setPadding(dp(28), dp(28), dp(28), dp(28))
        previewJob = activity.lifecycleScope.launch {
            val bitmap = runCatching {
                MediaThumbnailLoader.loadBitmap(activity, Uri.parse(avatarUri), 480, 480)
            }.getOrNull()
            if (photoPreview === preview && selectedAvatarUri == avatarUri && bitmap != null) {
                preview.setPadding(0, 0, 0, 0)
                preview.setImageBitmap(bitmap)
                preview.clearColorFilter()
            }
        }
    }


    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
