package com.bajianfeng.launcher.feature.phone

import android.Manifest
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class PhoneActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private val contactList = mutableListOf<ContactInfo>()
    private var selectedPhotoBitmap: Bitmap? = null
    private var photoPreview: ImageView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val PERMISSION_REQUEST = 2001
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedPhotoBitmap = loadImageFromUri(uri)
                photoPreview?.setImageBitmap(selectedPhotoBitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        recyclerView = findViewById(R.id.recycler_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.setHasFixedSize(false)
        recyclerView.setItemViewCacheSize(10)

        adapter = ContactAdapter(
            contactList,
            onContactClick = { contact -> makeCall(contact.phoneNumber) },
            onContactLongClick = { contact -> showContactOptions(contact) }
        )
        recyclerView.adapter = adapter

        findViewById<CardView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<CardView>(R.id.btn_add_contact).setOnClickListener { showAddContactDialog() }

        if (checkPermissions()) {
            loadContacts()
        } else {
            requestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadContacts()
            } else {
                Toast.makeText(this, "需要联系人和电话权限", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showContactOptions(contact: ContactInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_contact_options, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_contact_name).text = contact.name

        dialogView.findViewById<CardView>(R.id.btn_edit).setOnClickListener {
            dialog.dismiss()
            showEditContactDialog(contact)
        }
        dialogView.findViewById<CardView>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(contact)
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditContactDialog(contact: ContactInfo) {
        selectedPhotoBitmap = contact.photo

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_phone_contact, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_phone)
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)

        etName.setText(contact.name)
        etPhone.setText(contact.phoneNumber)
        contact.photo?.let { photoPreview?.setImageBitmap(it) }

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateContact(contact.id, name, phone, selectedPhotoBitmap)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showDeleteConfirmDialog(contact: ContactInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_remove_app, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            "确定要删除联系人 ${contact.name} 吗？"

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            deleteContact(contact.id)
        }
        dialog.show()
    }

    private fun updateContact(contactId: String, name: String, phone: String, photo: Bitmap?) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ops = ArrayList<ContentProviderOperation>()
                    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"

                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(selection, arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(selection, arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                            .build()
                    )

                    if (photo != null) {
                        val photoBytes = bitmapToBytes(photo)
                        val rawContactId = getRawContactId(contactId)
                        if (rawContactId != null) {
                            contentResolver.delete(
                                ContactsContract.Data.CONTENT_URI,
                                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                                arrayOf(rawContactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            )
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                                    .build()
                            )
                        }
                    }
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }
                Toast.makeText(this@PhoneActivity, "联系人已更新", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (e: Exception) {
                Toast.makeText(this@PhoneActivity, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getRawContactId(contactId: String): String? {
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun deleteContact(contactId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
                    contentResolver.delete(uri, null, null)
                }
                Toast.makeText(this@PhoneActivity, "联系人已删除", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (_: Exception) {
                Toast.makeText(this@PhoneActivity, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddContactDialog() {
        selectedPhotoBitmap = null

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_phone_contact, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_phone)
        photoPreview = dialogView.findViewById(R.id.iv_photo_preview)

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addContact(name, phone, selectedPhotoBitmap)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun loadImageFromUri(uri: Uri): Bitmap? {
        return try {
            val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            scaleBitmap(original, 512, 512)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    private fun addContact(name: String, phone: String, photo: Bitmap?) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ops = ArrayList<ContentProviderOperation>()

                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                            .build()
                    )

                    if (photo != null) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bitmapToBytes(photo))
                                .build()
                        )
                    }
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }
                Toast.makeText(this@PhoneActivity, "联系人已添加", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (e: Exception) {
                Toast.makeText(this@PhoneActivity, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadContacts() {
        scope.launch {
            val contacts = withContext(Dispatchers.IO) {
                val result = mutableListOf<ContactInfo>()
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    ),
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    while (cursor.moveToNext()) {
                        val photoUri = cursor.getString(photoIndex)
                        val photo = photoUri?.let { uri ->
                            try {
                                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                                contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                                    BitmapFactory.decodeStream(stream, null, options)
                                }
                            } catch (_: Exception) { null }
                        }
                        result.add(
                            ContactInfo(
                                cursor.getString(idIndex),
                                cursor.getString(nameIndex),
                                cursor.getString(numberIndex),
                                photo
                            )
                        )
                    }
                }
                result
            }
            contactList.clear()
            contactList.addAll(contacts)
            adapter.notifyDataSetChanged()
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$phoneNumber") })
        }
    }
}
