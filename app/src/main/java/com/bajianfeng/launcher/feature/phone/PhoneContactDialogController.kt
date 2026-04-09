package com.bajianfeng.launcher.feature.phone

import android.graphics.Bitmap
import android.net.Uri
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.PhoneContact
import com.bajianfeng.launcher.data.contact.PhoneContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PhoneContactDialogController(
    private val activity: ComponentActivity,
    private val scope: CoroutineScope,
    private val repository: PhoneContactRepository,
    private val runWithWritePermission: (() -> Unit) -> Unit,
    private val onContactsChanged: () -> Unit,
    private val onMessage: (String) -> Unit
) {
    private var selectedPhotoBitmap: Bitmap? = null
    private var photoPreview: ImageView? = null
    private val pickImageLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedPhotoBitmap = uri?.let(repository::loadImageFromUri)
        if (uri != null && selectedPhotoBitmap == null) {
            onMessage(activity.getString(R.string.pick_photo_failed))
        }
        updatePhotoPreview(selectedPhotoBitmap)
    }

    fun showAddContactDialog() {
        runWithWritePermission {
            showEditor(null)
        }
    }

    fun showContactOptions(contact: PhoneContact) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_contact_options, null)
        val dialog = android.app.AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_contact_name).text = contact.name
        dialogView.findViewById<CardView>(R.id.btn_edit).setOnClickListener {
            dialog.dismiss()
            runWithWritePermission {
                showEditor(contact)
            }
        }
        dialogView.findViewById<CardView>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            runWithWritePermission {
                showDeleteConfirmDialog(contact)
            }
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditor(contact: PhoneContact?) {
        selectedPhotoBitmap = contact?.photo
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_phone_contact, null)
        val dialog = android.app.AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_phone)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = activity.getString(
            if (contact == null) R.string.phone_contact_dialog_add_title
            else R.string.phone_contact_dialog_edit_title
        )
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)
        etName.setText(contact?.name.orEmpty())
        etPhone.setText(contact?.phoneNumber.orEmpty())
        updatePhotoPreview(contact?.photo)

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            openImagePicker()
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                onMessage(activity.getString(R.string.fill_required))
                return@setOnClickListener
            }
            scope.launch {
                runCatching {
                    if (contact == null) {
                        repository.addContact(name, phone, selectedPhotoBitmap)
                        onMessage(activity.getString(R.string.contact_added))
                    } else {
                        repository.updateContact(contact.id, name, phone, selectedPhotoBitmap)
                        onMessage(activity.getString(R.string.contact_updated))
                    }
                    onContactsChanged()
                }.onFailure {
                    onMessage(
                        activity.getString(
                            if (contact == null) R.string.contact_add_failed else R.string.contact_update_failed,
                            it.message.orEmpty()
                        )
                    )
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showDeleteConfirmDialog(contact: PhoneContact) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_remove_app, null)
        val dialog = android.app.AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.dialog_title).text =
            activity.getString(R.string.delete_contact_title)
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            activity.getString(R.string.delete_contact_message, contact.name)

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            scope.launch {
                runCatching {
                    repository.deleteContact(contact.id)
                    onMessage(activity.getString(R.string.contact_deleted))
                    onContactsChanged()
                }.onFailure {
                    onMessage(activity.getString(R.string.contact_delete_failed, it.message.orEmpty()))
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun updatePhotoPreview(bitmap: Bitmap?) {
        photoPreview?.apply {
            if (bitmap != null) {
                setImageBitmap(bitmap)
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
    }
}
