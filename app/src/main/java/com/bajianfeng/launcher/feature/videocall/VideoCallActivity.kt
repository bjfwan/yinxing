package com.bajianfeng.launcher.feature.videocall

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager
import com.bajianfeng.launcher.data.contact.ContactStorage
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

class VideoCallActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoCallContactAdapter
    private lateinit var manageAdapter: ContactManageAdapter
    private lateinit var modeActionButton: CardView
    private lateinit var modeActionText: TextView
    private lateinit var searchLayout: CardView
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: TextView
    private lateinit var stateView: PageStateView
    private lateinit var ttsService: TTSService
    private lateinit var contactManager: ContactManager
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var dialogController: VideoContactDialogController
    private lateinit var coordinator: VideoCallCoordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isManageMode = false
    private var searchQuery = ""
    private var allContacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        launcherPreferences = LauncherPreferences.getInstance(this)
        ttsService = TTSService(this)
        ttsService.initialize()
        contactManager = ContactManager.getInstance(this)

        recyclerView = findViewById(R.id.recycler_video_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(false)

        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        searchLayout = findViewById(R.id.layout_manage_search)
        searchInput = findViewById(R.id.et_contact_search)
        clearSearchButton = findViewById(R.id.btn_clear_search)
        stateView = findViewById(R.id.view_page_state)
        stateView.attachContent(recyclerView)

        adapter = VideoCallContactAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onContactClick = { contact -> coordinator.start(contact) }
        )
        manageAdapter = ContactManageAdapter(
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onPinClick = { contact -> togglePinned(contact) },
            onDeleteClick = { contact -> dialogController.showDeleteDialog(contact) }
        )

        coordinator = VideoCallCoordinator(
            activity = this,
            ttsService = ttsService,
            contactManager = contactManager,
            onNeedAccessibilityPermission = { dialogController.showAccessibilityDialog() },
            onNeedOverlayPermission = { contact -> dialogController.showOverlayPermissionDialog(contact) },
            onContactsChanged = { loadContacts() },
            onCallCompleted = { finish() }
        )
        dialogController = VideoContactDialogController(
            activity = this,
            onAddContact = { name, wechatId -> addContact(name, wechatId) },
            onDeleteContact = { contact -> deleteContact(contact) },
            onOpenAccessibilitySettings = { PermissionUtil.openAccessibilitySettings(this) },
            onOpenOverlaySettings = { PermissionUtil.openOverlaySettings(this) },
            onContinueWithoutOverlayPermission = { contact -> coordinator.continueWithVideoCall(contact) }
        )

        recyclerView.adapter = adapter
        applyPerformanceMode()

        searchInput.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty()
            updateSearchUi()
            renderContacts()
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
        }
        findViewById<CardView>(R.id.btn_back).setOnClickListener {
            if (isManageMode) {
                switchToCallMode()
            } else {
                finish()
            }
        }
        modeActionButton.setOnClickListener {
            if (isManageMode) {
                dialogController.showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }

        updateModeUi()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        applyPerformanceMode()
        loadContacts()
    }

    override fun onDestroy() {
        coordinator.clear()
        ttsService.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyPerformanceMode() {
        val lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled()
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 3 else 8)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
        searchLayout.cardElevation = resources.displayMetrics.density * if (lowPerformanceMode) 2 else 4
        adapter.setLowPerformanceMode(lowPerformanceMode)
        manageAdapter.setLowPerformanceMode(lowPerformanceMode)
    }

    private fun switchToManageMode() {
        isManageMode = true
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = manageAdapter
        updateModeUi()
        renderContacts()
    }

    private fun switchToCallMode() {
        isManageMode = false
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        updateModeUi()
        if (searchQuery.isNotBlank()) {
            searchInput.text?.clear()
        } else {
            renderContacts()
        }
    }

    private fun updateModeUi() {
        val text = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = text
        modeActionButton.contentDescription = text
        searchLayout.isVisible = isManageMode
        updateSearchUi()
    }

    private fun updateSearchUi() {
        clearSearchButton.isVisible = isManageMode && searchQuery.isNotBlank()
    }

    private fun addContact(name: String, wechatId: String) {
        contactManager.addContact(
            Contact(
                id = UUID.randomUUID().toString(),
                name = name,
                wechatId = wechatId.ifBlank { name }
            )
        )
        loadContacts()
        if (searchQuery.isNotBlank()) {
            searchInput.text?.clear()
        }
        Toast.makeText(this, getString(R.string.contact_added_named, name), Toast.LENGTH_SHORT).show()
    }

    private fun deleteContact(contact: Contact) {
        contactManager.removeContact(contact.id)
        loadContacts()
        Toast.makeText(this, getString(R.string.contact_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun togglePinned(contact: Contact) {
        contactManager.updateContact(contact.copy(isPinned = !contact.isPinned))
        loadContacts()
        Toast.makeText(
            this,
            getString(
                if (contact.isPinned) R.string.video_contact_unpinned else R.string.video_contact_pinned,
                contact.name
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun loadContacts() {
        allContacts = contactManager.getContacts()
        renderContacts()
    }

    private fun renderContacts() {
        val contacts = ContactStorage.filter(allContacts, if (isManageMode) searchQuery else "")
        adapter.submitList(contacts)
        manageAdapter.submitList(contacts)
        updateState(contacts)
    }

    private fun updateState(contacts: List<Contact>) {
        if (contacts.isNotEmpty()) {
            stateView.hide()
            return
        }

        if (isManageMode && searchQuery.isNotBlank() && allContacts.isNotEmpty()) {
            stateView.show(
                title = getString(R.string.state_video_search_empty_title),
                message = getString(R.string.state_video_search_empty_message, searchQuery.trim()),
                actionText = getString(R.string.action_clear)
            ) {
                searchInput.text?.clear()
            }
            return
        }

        stateView.show(
            title = getString(R.string.state_video_empty_title),
            message = getString(
                if (isManageMode) {
                    R.string.state_video_manage_empty_message
                } else {
                    R.string.state_video_empty_message
                }
            ),
            actionText = getString(
                if (isManageMode) {
                    R.string.state_video_empty_action_add
                } else {
                    R.string.state_video_empty_action_manage
                }
            )
        ) {
            if (isManageMode) {
                dialogController.showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }
    }
}
