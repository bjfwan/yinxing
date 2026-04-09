package com.bajianfeng.launcher.feature.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.common.util.PermissionRequestHandler
import com.bajianfeng.launcher.data.contact.PhoneContact
import com.bajianfeng.launcher.data.contact.PhoneContactRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var stateView: PageStateView
    private lateinit var dialogController: PhoneContactDialogController
    private lateinit var contactRepository: PhoneContactRepository
    private lateinit var readContactsPermissionHandler: PermissionRequestHandler
    private lateinit var writeContactsPermissionHandler: PermissionRequestHandler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val readContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val handled = readContactsPermissionHandler.handleResult(Manifest.permission.READ_CONTACTS, granted)
        if (!granted) {
            showToast(getString(R.string.permission_read_contacts_denied))
            showPermissionDeniedState()
        } else if (!handled) {
            loadContacts()
        }
    }

    private val writeContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val handled = writeContactsPermissionHandler.handleResult(Manifest.permission.WRITE_CONTACTS, granted)
        if (!granted && !handled) {
            showToast(getString(R.string.permission_write_contacts_denied))
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        scope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.IO) {
                    contactRepository.loadImage(uri)
                }
            }.getOrNull()
            dialogController.updateSelectedPhoto(bitmap)
            if (bitmap == null) {
                showToast(getString(R.string.pick_photo_failed))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        launcherPreferences = LauncherPreferences.getInstance(this)
        contactRepository = PhoneContactRepository(this)

        recyclerView = findViewById(R.id.recycler_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.setHasFixedSize(false)

        stateView = findViewById(R.id.view_page_state)
        stateView.attachContent(recyclerView)

        adapter = ContactAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onContactClick = { contact -> makeCall(contact.phoneNumber) },
            onContactLongClick = { contact -> showContactOptions(contact) }
        )
        recyclerView.adapter = adapter

        dialogController = PhoneContactDialogController(
            activity = this,
            onPickPhoto = { openImagePicker() },
            onAddContact = { name, phone, photo -> addContact(name, phone, photo) },
            onUpdateContact = { contact, name, phone, photo -> updateContact(contact.id, name, phone, photo) },
            onDeleteContact = { contact -> deleteContact(contact.id) }
        )

        readContactsPermissionHandler = PermissionRequestHandler(
            hasPermission = ::hasPermission,
            requestPermission = readContactsPermissionLauncher::launch
        )
        writeContactsPermissionHandler = PermissionRequestHandler(
            hasPermission = ::hasPermission,
            requestPermission = writeContactsPermissionLauncher::launch
        )

        applyPerformanceMode()

        findViewById<CardView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<CardView>(R.id.btn_add_contact).setOnClickListener {
            ensureWriteContactsPermission {
                dialogController.showAddDialog()
            }
        }

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            showPermissionDeniedState()
            requestReadContactsPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        applyPerformanceMode()
        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            showPermissionDeniedState()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun applyPerformanceMode() {
        val lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled()
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 2 else 10)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
        adapter.setLowPerformanceMode(lowPerformanceMode)
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestReadContactsPermission() {
        readContactsPermissionHandler.runOrRequest(Manifest.permission.READ_CONTACTS) {
            loadContacts()
        }
    }

    private fun ensureWriteContactsPermission(action: () -> Unit) {
        writeContactsPermissionHandler.runOrRequest(Manifest.permission.WRITE_CONTACTS, action)
    }

    private fun showContactOptions(contact: PhoneContact) {
        dialogController.showOptions(
            contact = contact,
            onEditRequested = {
                ensureWriteContactsPermission {
                    dialogController.showEditDialog(contact)
                    preloadContactPhoto(contact)
                }
            },
            onDeleteRequested = {
                ensureWriteContactsPermission {
                    dialogController.showDeleteDialog(contact)
                }
            }
        )
    }

    private fun preloadContactPhoto(contact: PhoneContact) {
        val photoUri = contact.photoUri ?: return
        scope.launch {
            val previewSize = if (launcherPreferences.isLowPerformanceModeEnabled()) 192 else 256
            val bitmap = MediaThumbnailLoader.loadBitmap(
                this@PhoneActivity,
                Uri.parse(photoUri),
                previewSize,
                previewSize
            )
            dialogController.updateSelectedPhoto(bitmap)
        }
    }

    private fun addContact(name: String, phone: String, photo: android.graphics.Bitmap?) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contactRepository.addContact(name, phone, photo)
                }
            }.onSuccess {
                showToast(getString(R.string.contact_added))
                refreshContacts()
            }.onFailure { error ->
                showToast(getString(R.string.contact_add_failed, error.message ?: ""))
            }
        }
    }

    private fun updateContact(contactId: String, name: String, phone: String, photo: android.graphics.Bitmap?) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contactRepository.updateContact(contactId, name, phone, photo)
                }
            }.onSuccess {
                showToast(getString(R.string.contact_updated))
                refreshContacts()
            }.onFailure { error ->
                showToast(getString(R.string.contact_update_failed, error.message ?: ""))
            }
        }
    }

    private fun deleteContact(contactId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contactRepository.deleteContact(contactId)
                }
            }.onSuccess {
                showToast(getString(R.string.contact_deleted))
                refreshContacts()
            }.onFailure { error ->
                showToast(getString(R.string.contact_delete_failed, error.message ?: ""))
            }
        }
    }

    private fun refreshContacts() {
        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            showToast(getString(R.string.contact_list_not_refreshed))
            showPermissionDeniedState()
        }
    }

    private fun loadContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            adapter.submitList(emptyList())
            showPermissionDeniedState()
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contactRepository.getContacts()
                }
            }.onSuccess { contacts ->
                adapter.submitList(contacts)
                if (contacts.isEmpty()) {
                    showEmptyState()
                } else {
                    stateView.hide()
                }
            }.onFailure { error ->
                val message = getString(R.string.contact_load_failed, error.message ?: "")
                showToast(message)
                showErrorState(message)
            }
        }
    }

    private fun showPermissionDeniedState() {
        stateView.show(
            title = getString(R.string.state_phone_permission_title),
            message = getString(R.string.state_phone_permission_message),
            actionText = getString(R.string.state_phone_permission_action)
        ) {
            requestReadContactsPermission()
        }
    }

    private fun showEmptyState() {
        stateView.show(
            title = getString(R.string.state_phone_empty_title),
            message = getString(R.string.state_phone_empty_message),
            actionText = getString(R.string.state_phone_empty_action)
        ) {
            ensureWriteContactsPermission {
                dialogController.showAddDialog()
            }
        }
    }

    private fun showErrorState(message: String) {
        stateView.show(
            title = getString(R.string.state_phone_error_title),
            message = message,
            actionText = getString(R.string.state_phone_error_action)
        ) {
            loadContacts()
        }
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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

        runCatching {
            startActivity(callIntent)
        }.onFailure { error ->
            showToast(getString(R.string.dial_failed, error.message ?: ""))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
