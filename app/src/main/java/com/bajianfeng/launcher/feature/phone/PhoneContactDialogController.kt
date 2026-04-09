package com.bajianfeng.launcher.feature.phone

import android.graphics.Bitmap
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.PhoneContact

class PhoneContactDialogController(
    private val activity: AppCompatActivity,
    private val onPickPhoto: () -> Unit,
    private val onAddContact: (String, String, Bitmap?) -> Unit,
    private val onUpdateContact: (PhoneContact, String, String, Bitmap?) -> Unit,
    private val onDeleteContact: (PhoneContact) -> Unit
) {
    private var selectedPhoto: Bitmap? = null
    private var photoPreview: ImageView? = null

    fun updateSelectedPhoto(bitmap: Bitmap?) {
        selectedPhoto = bitmap
        val preview = photoPreview ?: return
        if (bitmap != null) {
            preview.setImageBitmap(bitmap)
        } else {
            preview.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    fun showOptions(
        contact: PhoneContact,
        onEditRequested: () -> Unit,
        onDeleteRequested: () -> Unit
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_contact_options, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_contact_name).text = contact.name
        dialogView.findViewById<CardView>(R.id.btn_edit).setOnClickListener {
            dialog.dismiss()
            onEditRequested()
        }
        dialogView.findViewById<CardView>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            onDeleteRequested()
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showAddDialog() {
        showEditorDialog(
            title = activity.getString(R.string.phone_contact_dialog_add_title),
            initialContact = null
        ) { name, phone, photo ->
            onAddContact(name, phone, photo)
        }
    }

    fun showEditDialog(contact: PhoneContact) {
        showEditorDialog(
            title = activity.getString(R.string.phone_contact_dialog_edit_title),
            initialContact = contact
        ) { name, phone, photo ->
            onUpdateContact(contact, name, phone, photo)
        }
    }

    fun showDeleteDialog(contact: PhoneContact) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_remove_app, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.delete_contact_title)
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            activity.getString(R.string.delete_contact_message, contact.name)
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            onDeleteContact(contact)
        }
        dialog.show()
    }

    private fun showEditorDialog(
        title: String,
        initialContact: PhoneContact?,
        onConfirm: (String, String, Bitmap?) -> Unit
    ) {
        selectedPhoto = null
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_phone_contact, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = title

        val nameField = dialogView.findViewById<EditText>(R.id.et_name)
        val phoneField = dialogView.findViewById<EditText>(R.id.et_phone)
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)

        nameField.setText(initialContact?.name.orEmpty())
        phoneField.setText(initialContact?.phoneNumber.orEmpty())
        updateSelectedPhoto(selectedPhoto)

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            onPickPhoto()
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = nameField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.fill_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onConfirm(name, phone, selectedPhoto)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            photoPreview = null
            selectedPhoto = null
        }
        dialog.show()
    }
}
