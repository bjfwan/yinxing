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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PhoneActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private val contactList = mutableListOf<ContactInfo>()
    private var selectedPhotoBitmap: Bitmap? = null
    private var photoPreview: ImageView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingWriteAction: (() -> Unit)? = null
    private var pendingMediaAction: (() -> Unit)? = null

    private val readContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContacts()
        } else {
            showToast("未授予联系人读取权限")
        }
    }

    private val writeContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingWriteAction
        pendingWriteAction = null
        if (granted) {
            action?.invoke()
        } else {
            showToast("未授予联系人编辑权限")
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingMediaAction
        pendingMediaAction = null
        if (granted) {
            action?.invoke()
        } else {
            showToast("未授予图片读取权限")
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedPhotoBitmap = loadImageFromUri(uri)
                if (selectedPhotoBitmap == null) {
                    showToast("图片读取失败")
                }
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
        findViewById<CardView>(R.id.btn_add_contact).setOnClickListener {
            ensureWriteContactsPermission {
                showAddContactDialog()
            }
        }

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            readContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureWriteContactsPermission(action: () -> Unit) {
        if (hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            action()
            return
        }
        pendingWriteAction = action
        writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
    }

    private fun ensureMediaPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
            action()
            return
        }
        pendingMediaAction = action
        mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
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
            ensureWriteContactsPermission {
                showEditContactDialog(contact)
            }
        }
        dialogView.findViewById<CardView>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            ensureWriteContactsPermission {
                showDeleteConfirmDialog(contact)
            }
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
            ensureMediaPermission {
                openImagePicker()
            }
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                showToast("请填写完整信息")
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
                showToast("联系人已更新")
                refreshContacts()
            } catch (e: Exception) {
                showToast("更新失败: ${e.message}")
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
            if (it.moveToFirst()) {
                return it.getString(0)
            }
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
                showToast("联系人已删除")
                refreshContacts()
            } catch (e: Exception) {
                showToast("删除失败: ${e.message}")
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
        photoPreview?.setImageDrawable(null)

        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            ensureMediaPermission {
                openImagePicker()
            }
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                showToast("请填写完整信息")
                return@setOnClickListener
            }
            addContact(name, phone, selectedPhotoBitmap)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
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
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
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
                showToast("联系人已添加")
                refreshContacts()
            } catch (e: Exception) {
                showToast("添加失败: ${e.message}")
            }
        }
    }

    private fun refreshContacts() {
        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            showToast("未授予联系人读取权限，列表未刷新")
        }
    }

    private fun loadContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            contactList.clear()
            adapter.notifyDataSetChanged()
            return
        }

        scope.launch {
            try {
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
                        null,
                        null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                        while (cursor.moveToNext()) {
                            val photoUri = cursor.getString(photoIndex)
                            val photo = photoUri?.let { uri ->
                                try {
                                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                                    contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                                        BitmapFactory.decodeStream(stream, null, options)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
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
            } catch (e: Exception) {
                showToast("联系人加载失败: ${e.message}")
            }
        }
    }

    private fun makeCall(phoneNumber: String) {
        val callIntent = if (hasPermission(Manifest.permission.CALL_PHONE)) {
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
        }

        try {
            startActivity(callIntent)
        } catch (e: Exception) {
            showToast("拨号失败: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
