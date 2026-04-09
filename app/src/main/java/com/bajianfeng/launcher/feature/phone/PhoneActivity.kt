package com.bajianfeng.launcher.feature.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.common.util.PermissionRequestHandler
import com.bajianfeng.launcher.data.contact.PhoneContact
import com.bajianfeng.launcher.data.contact.PhoneContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private lateinit var pageStateView: PageStateView
    private lateinit var contactRepository: PhoneContactRepository
    private lateinit var permissionHandler: PermissionRequestHandler
    private lateinit var dialogController: PhoneContactDialogController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val readContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionHandler.handleResult(Manifest.permission.READ_CONTACTS, granted)
    }

    private val writeContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionHandler.handleResult(Manifest.permission.WRITE_CONTACTS, granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        contactRepository = PhoneContactRepository(this)
        permissionHandler = PermissionRequestHandler(
            hasPermission = ::hasPermission,
            requestPermission = { permission ->
                when (permission) {
                    Manifest.permission.READ_CONTACTS -> readContactsPermissionLauncher.launch(permission)
                    Manifest.permission.WRITE_CONTACTS -> writeContactsPermissionLauncher.launch(permission)
                }
            }
        )
        dialogController = PhoneContactDialogController(
            activity = this,
            scope = scope,
            repository = contactRepository,
            runWithWritePermission = ::runWithWriteContactsPermission,
            onContactsChanged = ::loadContacts,
            onMessage = ::showToast
        )

        recyclerView = findViewById(R.id.recycler_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.setHasFixedSize(false)
        recyclerView.setItemViewCacheSize(10)

        pageStateView = findViewById(R.id.view_page_state)
        adapter = ContactAdapter(
            onContactClick = ::makeCall,
            onContactLongClick = { dialogController.showContactOptions(it) }
        )
        recyclerView.adapter = adapter

        findViewById<CardView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<CardView>(R.id.btn_add_contact).setOnClickListener {
            dialogController.showAddContactDialog()
        }

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            loadContacts()
        } else {
            showPermissionDeniedState()
            requestReadContacts()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun requestReadContacts() {
        permissionHandler.runOrRequest(
            Manifest.permission.READ_CONTACTS,
            onGranted = ::loadContacts,
            onDenied = {
                showToast(getString(R.string.permission_read_contacts_denied))
                showPermissionDeniedState()
            }
        )
    }

    private fun runWithWriteContactsPermission(action: () -> Unit) {
        permissionHandler.runOrRequest(
            Manifest.permission.WRITE_CONTACTS,
            onGranted = action,
            onDenied = {
                showToast(getString(R.string.permission_write_contacts_denied))
            }
        )
    }

    private fun loadContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            adapter.submitList(emptyList())
            showPermissionDeniedState()
            return
        }

        scope.launch {
            runCatching {
                contactRepository.getContacts()
            }.onSuccess(::showContacts).onFailure {
                val message = getString(R.string.contact_load_failed, it.message.orEmpty())
                showToast(message)
                showErrorState(message)
            }
        }
    }

    private fun showContacts(contacts: List<PhoneContact>) {
        adapter.submitList(contacts)
        if (contacts.isEmpty()) {
            showEmptyState()
            return
        }
        recyclerView.isVisible = true
        pageStateView.hide()
    }

    private fun showPermissionDeniedState() {
        recyclerView.isVisible = false
        pageStateView.show(
            getString(R.string.state_phone_permission_title),
            getString(R.string.state_phone_permission_message),
            getString(R.string.state_phone_permission_action),
            ::requestReadContacts
        )
    }

    private fun showEmptyState() {
        recyclerView.isVisible = false
        pageStateView.show(
            getString(R.string.state_phone_empty_title),
            getString(R.string.state_phone_empty_message),
            getString(R.string.state_phone_empty_action)
        ) {
            dialogController.showAddContactDialog()
        }
    }

    private fun showErrorState(message: String) {
        recyclerView.isVisible = false
        pageStateView.show(
            getString(R.string.state_phone_error_title),
            message,
            getString(R.string.state_phone_error_action),
            ::loadContacts
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun makeCall(contact: PhoneContact) {
        val intent = if (hasPermission(Manifest.permission.CALL_PHONE)) {
            Intent(Intent.ACTION_CALL).apply {
                data = "tel:${contact.phoneNumber}".toUri()
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                data = "tel:${contact.phoneNumber}".toUri()
            }
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            showToast(getString(R.string.dial_failed, it.message.orEmpty()))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
