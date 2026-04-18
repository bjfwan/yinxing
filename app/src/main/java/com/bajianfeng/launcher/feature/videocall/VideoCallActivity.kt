package com.bajianfeng.launcher.feature.videocall

import android.content.Context
import android.content.Intent

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactAvatarStore
import com.bajianfeng.launcher.data.contact.ContactManager
import com.bajianfeng.launcher.data.contact.ContactStorage
import com.bajianfeng.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

class VideoCallActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_START_IN_MANAGE_MODE = "extra_start_in_manage_mode"

        fun createIntent(context: Context, startInManageMode: Boolean = false): Intent {
            return Intent(context, VideoCallActivity::class.java)
                .putExtra(EXTRA_START_IN_MANAGE_MODE, startInManageMode)
        }
    }

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: VideoCallContactAdapter
    private lateinit var manageAdapter: ContactManageAdapter
    private lateinit var pageTitleText: TextView
    private lateinit var modeActionButton: CardView
    private lateinit var modeActionText: TextView
    private lateinit var modeSummaryText: TextView

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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            dialogController.updateSelectedPhoto(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        launcherPreferences = LauncherPreferences.getInstance(this)
        ttsService = TTSService(this)
        ttsService.initialize()
        contactManager = ContactManager.getInstance(this)

        recyclerView = findViewById(R.id.recycler_video_contacts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(false)


        pageTitleText = findViewById(R.id.tv_page_title)
        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        modeSummaryText = findViewById(R.id.tv_mode_summary)

        searchLayout = findViewById(R.id.layout_manage_search)
        searchInput = findViewById(R.id.et_contact_search)
        clearSearchButton = findViewById(R.id.btn_clear_search)
        stateView = findViewById(R.id.view_page_state)
        stateView.attachContent(recyclerView)

        adapter = VideoCallContactAdapter(
            scope = scope,
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onContactClick = { contact -> coordinator.start(contact) },
            onWechatVideoClick = { contact -> coordinator.start(contact) }
        )

        manageAdapter = ContactManageAdapter(
            lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled(),
            onEditClick = { contact -> dialogController.showEditContactDialog(contact) },
            onDeleteClick = { contact -> dialogController.showDeleteDialog(contact) }
        )

        coordinator = VideoCallCoordinator(
            activity = this,
            ttsService = ttsService,
            contactManager = contactManager,
            automationGateway = SelectToSpeakAutomationGateway,
            onNeedAccessibilityPermission = { dialogController.showAccessibilityDialog() },
            onNeedOverlayPermission = { contact -> dialogController.showOverlayPermissionDialog(contact) },
            onCallCompleted = { loadContacts() }
        )

        dialogController = VideoContactDialogController(
            activity = this,
            onPickPhoto = { openImagePicker() },
            onSaveContact = { original, name, wechatId, avatarUri ->
                saveContact(original, name, wechatId, avatarUri)
            },
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

        if (intent.getBooleanExtra(EXTRA_START_IN_MANAGE_MODE, false)) {
            switchToManageMode()
        } else {
            updateModeUi()
        }
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
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        updateModeUi()
        if (searchQuery.isNotBlank()) {
            searchInput.text?.clear()
        } else {
            renderContacts()
        }
    }


    private fun updateModeUi() {
        pageTitleText.text = getString(
            if (isManageMode) R.string.video_manage_title else R.string.video_title
        )
        val actionText = getString(R.string.action_add)
        modeActionText.text = actionText
        modeActionButton.contentDescription = actionText
        modeActionButton.isVisible = isManageMode
        modeSummaryText.text = getString(
            if (isManageMode) R.string.video_mode_manage_summary
            else R.string.video_mode_call_summary
        )
        searchLayout.isVisible = isManageMode
        updateSearchUi()
    }


    private fun updateSearchUi() {
        clearSearchButton.isVisible = isManageMode && searchQuery.isNotBlank()
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun saveContact(
        original: Contact?,
        name: String,
        wechatId: String,
        avatarUri: String?
    ) {
        val contactId = original?.id ?: UUID.randomUUID().toString()
        val resolvedAvatarUri = resolveAvatarUri(contactId, original?.avatarUri, avatarUri)
        val contact = Contact(
            id = contactId,
            name = name,
            wechatId = wechatId.trim().takeIf { it.isNotBlank() },
            avatarUri = resolvedAvatarUri,
            preferredAction = Contact.PreferredAction.WECHAT_VIDEO,
            isPinned = original?.isPinned ?: false,
            callCount = original?.callCount ?: 0,
            lastCallTime = original?.lastCallTime ?: 0,
            searchKeywords = original?.searchKeywords ?: emptyList()
        )
        if (original == null) {
            contactManager.addContact(contact)
            showToast(getString(R.string.contact_added_named, name))
        } else {
            contactManager.updateContact(contact)
            showToast(getString(R.string.contact_updated))
        }
        loadContacts()
    }

    private fun resolveAvatarUri(contactId: String, previousAvatarUri: String?, selectedAvatarUri: String?): String? {
        if (selectedAvatarUri.isNullOrBlank()) {
            return previousAvatarUri
        }
        if (selectedAvatarUri == previousAvatarUri) {
            return previousAvatarUri
        }
        val savedAvatarUri = ContactAvatarStore.saveFromUri(this, Uri.parse(selectedAvatarUri), contactId)
            ?: return previousAvatarUri
        if (!previousAvatarUri.isNullOrBlank() && previousAvatarUri != savedAvatarUri) {
            ContactAvatarStore.deleteOwnedAvatar(this, previousAvatarUri)
        }
        return savedAvatarUri
    }

    private fun deleteContact(contact: Contact) {
        ContactAvatarStore.deleteOwnedAvatar(this, contact.avatarUri)
        contactManager.removeContact(contact.id)
        loadContacts()
        showToast(getString(R.string.contact_deleted))
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
                    R.string.action_back_home
                }
            )
        ) {
            if (isManageMode) {
                dialogController.showAddContactDialog()
            } else {
                finish()
            }
        }

    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
