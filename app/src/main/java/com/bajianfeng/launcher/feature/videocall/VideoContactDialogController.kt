package com.bajianfeng.launcher.feature.videocall

import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager
import java.util.UUID

class VideoContactDialogController(
    private val activity: AppCompatActivity,
    private val contactManager: ContactManager,
    private val onContactsChanged: () -> Unit
) {
    fun showAddContactDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val dialog = android.app.AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val nameInput = dialogView.findViewById<EditText>(R.id.et_contact_name)
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            contactManager.addContact(
                Contact(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    wechatId = name
                )
            )
            onContactsChanged()
            Toast.makeText(activity, activity.getString(R.string.contact_added_named, name), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showDeleteDialog(contact: Contact) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.delete_contact_title)
            .setMessage(activity.getString(R.string.video_contact_delete_message, contact.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                contactManager.removeContact(contact.id)
                onContactsChanged()
                Toast.makeText(activity, activity.getString(R.string.contact_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
