package com.yinxing.launcher.feature.videocall

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
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.common.service.TTSService
import com.yinxing.launcher.common.ui.PageStateView
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactAvatarStore
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.data.contact.ContactStorage
import com.yinxing.launcher.data.home.LauncherPreferences
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
    private lateinit var modeActionButton: MaterialCardView
    private lateinit var modeActionText: TextView
    private lateinit var modeSummaryText: TextView
    private lateinit var searchLayout: MaterialCardView
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: MaterialCardView
    private lateinit var stateView: PageStateView
    private lateinit var ttsService: TTSService
    private lateinit var contactManager: ContactManager
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var dialogController: VideoContactDialogController
    private lateinit var coordinator: VideoCallCoordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isManageMode = false
    private var launchedFromManageEntry = false
    private var searchQuery = ""
    private var allContacts: List<Contact> = emptyList()
    private var loadJob: Job? = null

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

        LobsterClient.log("[微信视频] VideoCallActivity 已打开")

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
            onCallCompleted = { refreshContacts() }
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

        launchedFromManageEntry = intent.getBooleanExtra(EXTRA_START_IN_MANAGE_MODE, false)

        searchInput.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty()
            updateSearchUi()
            renderContacts()
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
        }
        findViewById<MaterialCardView>(R.id.btn_back).setOnClickListener {
            if (isManageMode && !launchedFromManageEntry) {
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

        if (launchedFromManageEntry) {
            switchToManageMode()
        } else {
            updateModeUi()
            renderContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        applyPerformanceMode()
        adapter.setFullCardTapEnabled(launcherPreferences.isFullCardTapEnabled())
        refreshContacts()
    }

    override fun onDestroy() {
        LobsterClient.report(this, "微信视频页面关闭")
        coordinator.clear()
        ttsService.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyPerformanceMode() {
        val lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled()
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 3 else 8)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
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
        val actionText = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = actionText
        modeActionButton.contentDescription = actionText
        modeActionButton.isVisible = true
        modeSummaryText.text = getString(
            if (isManageMode) R.string.video_mode_manage_summary else R.string.video_mode_call_summary
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
        scope.launch {
            val failure = if (original == null) R.string.contact_add_failed else R.string.contact_update_failed
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    val previousAvatar = original?.avatarUri
                    val selectedAvatar = avatarUri
                    val contactId = original?.id ?: UUID.randomUUID().toString()
                    var createdAvatar: String? = null
                    val resolvedAvatarUri = when {
                        selectedAvatar.isNullOrBlank() -> previousAvatar
                        selectedAvatar == previousAvatar -> previousAvatar
                        else -> ContactAvatarStore.saveFromUri(this@VideoCallActivity, Uri.parse(selectedAvatar), contactId)
                            ?.also { if (it != previousAvatar) createdAvatar = it }
                            ?: previousAvatar
                    }
                    try {
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
                        } else {
                            contactManager.updateContact(contact)
                        }
                        if (!previousAvatar.isNullOrBlank() && previousAvatar != resolvedAvatarUri) {
                            ContactAvatarStore.deleteOwnedAvatar(this@VideoCallActivity, previousAvatar)
                        }
                    } catch (throwable: Throwable) {
                        if (!createdAvatar.isNullOrBlank()) {
                            ContactAvatarStore.deleteOwnedAvatar(this@VideoCallActivity, createdAvatar)
                        }
                        throw throwable
                    }
                }
                refreshContacts()
                if (original == null) {
                    showToast(getString(R.string.contact_added_named, name))
                } else {
                    showToast(getString(R.string.contact_updated))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showToast(getString(failure, throwable.message.orEmpty()))
            }
        }
    }

    private fun deleteContact(contact: Contact) {
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    contactManager.removeContact(contact.id)
                    ContactAvatarStore.deleteOwnedAvatar(this@VideoCallActivity, contact.avatarUri)
                }
                refreshContacts()
                showToast(getString(R.string.contact_deleted))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showToast(getString(R.string.contact_delete_failed, throwable.message.orEmpty()))
            }
        }
    }


    private fun refreshContacts() {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val contacts = withContext(Dispatchers.IO) { contactManager.getContacts() }
                allContacts = contacts
                contacts.forEach { contact ->
                    contact.avatarUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { runCatching { MediaThumbnailLoader.evictFailedUri(Uri.parse(it)) } }
                }
                renderContacts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showToast(getString(R.string.contact_load_failed, throwable.message.orEmpty()))
            }
        }
    }

    private fun renderContacts() {
        val contacts = if (isManageMode && searchQuery.isNotBlank()) {
            ContactStorage.filter(allContacts, searchQuery)
        } else {
            allContacts
        }
        if (isManageMode) {
            manageAdapter.submitList(contacts)
        } else {
            adapter.submitList(contacts)
        }
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
