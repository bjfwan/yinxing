package com.yinxing.launcher.feature.videocall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterReportStatus
import com.yinxing.launcher.common.service.TTSService
import com.yinxing.launcher.common.ui.PageStateView
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


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
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var dialogController: VideoContactDialogController
    private lateinit var coordinator: VideoCallCoordinator
    private lateinit var viewModel: VideoCallViewModel
    private var launchedFromManageEntry = false
    private var searchInputUpdating = false

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
        viewModel = ViewModelProvider(this, VideoCallViewModel.Factory(this))[VideoCallViewModel::class.java]

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
            scope = lifecycleScope,
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
            contactManager = ContactManager.getInstance(this),
            automationGateway = SelectToSpeakAutomationGateway,
            onNeedAccessibilityPermission = { dialogController.showAccessibilityDialog() },
            onNeedOverlayPermission = { contact -> dialogController.showOverlayPermissionDialog(contact) },
            onCallCompleted = { viewModel.refresh() }
        )

        dialogController = VideoContactDialogController(
            activity = this,
            onPickPhoto = { openImagePicker() },
            onSaveContact = { original, name, wechatId, avatarUri ->
                viewModel.saveContact(original, name, wechatId, avatarUri)
            },
            onDeleteContact = { contact -> viewModel.deleteContact(contact) },
            onOpenAccessibilitySettings = { PermissionUtil.openAccessibilitySettings(this) },
            onOpenOverlaySettings = { PermissionUtil.openOverlaySettings(this) },
            onContinueWithoutOverlayPermission = { contact -> coordinator.continueWithVideoCall(contact) }
        )

        recyclerView.adapter = adapter
        applyPerformanceMode()

        launchedFromManageEntry = intent.getBooleanExtra(EXTRA_START_IN_MANAGE_MODE, false)

        searchInput.doAfterTextChanged { editable ->
            if (searchInputUpdating) return@doAfterTextChanged
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
        }
        findViewById<MaterialCardView>(R.id.btn_back).setOnClickListener {
            if (viewModel.isManageMode.value && !launchedFromManageEntry) {
                viewModel.setManageMode(false)
            } else {
                finish()
            }
        }
        modeActionButton.setOnClickListener {
            if (viewModel.isManageMode.value) {
                dialogController.showAddContactDialog()
            } else {
                viewModel.setManageMode(true)
            }
        }

        observeViewModel()

        if (launchedFromManageEntry) {
            viewModel.setManageMode(true)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isManageMode.collect { manageMode ->
                        recyclerView.layoutManager = LinearLayoutManager(this@VideoCallActivity)
                        recyclerView.adapter = if (manageMode) manageAdapter else adapter
                        updateModeUi(manageMode)
                    }
                }
                launch {
                    viewModel.searchQuery.collect { query ->
                        if (searchInput.text?.toString().orEmpty() != query) {
                            searchInputUpdating = true
                            searchInput.setText(query)
                            searchInput.setSelection(query.length)
                            searchInputUpdating = false
                        }
                        clearSearchButton.isVisible =
                            viewModel.isManageMode.value && query.isNotBlank()
                    }
                }
                launch {
                    viewModel.visibleContacts.collect { contacts ->
                        if (viewModel.isManageMode.value) {
                            manageAdapter.submitList(contacts)
                        } else {
                            adapter.submitList(contacts)
                        }
                        updateState(contacts)
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun handleEvent(event: VideoCallViewModel.Event) {
        when (event) {
            is VideoCallViewModel.Event.LoadError ->
                showToast(getString(R.string.contact_load_failed, event.throwable.message.orEmpty()))
            is VideoCallViewModel.Event.ContactAdded ->
                showToast(getString(R.string.contact_added_named, event.name))
            is VideoCallViewModel.Event.ContactUpdated ->
                showToast(getString(R.string.contact_updated))
            is VideoCallViewModel.Event.ContactDeleted ->
                showToast(getString(R.string.contact_deleted))
            is VideoCallViewModel.Event.SaveError -> {
                val msg = if (event.isAdd) R.string.contact_add_failed else R.string.contact_update_failed
                showToast(getString(msg, event.throwable.message.orEmpty()))
            }
            is VideoCallViewModel.Event.DeleteError ->
                showToast(getString(R.string.contact_delete_failed, event.throwable.message.orEmpty()))
        }
    }

    override fun onResume() {
        super.onResume()
        applyPerformanceMode()
        adapter.setFullCardTapEnabled(launcherPreferences.isFullCardTapEnabled())
        viewModel.refresh()
    }

    override fun onDestroy() {
        LobsterClient.report(this, "微信视频页面", LobsterReportStatus.REPORTED, "微信视频页面关闭")
        coordinator.clear()
        ttsService.shutdown()
        super.onDestroy()
    }

    private fun applyPerformanceMode() {
        val lowPerformanceMode = launcherPreferences.isLowPerformanceModeEnabled()
        recyclerView.setItemViewCacheSize(if (lowPerformanceMode) 3 else 8)
        recyclerView.itemAnimator = if (lowPerformanceMode) null else DefaultItemAnimator()
        adapter.setLowPerformanceMode(lowPerformanceMode)
        manageAdapter.setLowPerformanceMode(lowPerformanceMode)
    }

    private fun updateModeUi(isManageMode: Boolean) {
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
        clearSearchButton.isVisible = isManageMode && viewModel.searchQuery.value.isNotBlank()
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun updateState(contacts: List<Contact>) {
        if (contacts.isNotEmpty()) {
            stateView.hide()
            return
        }

        val isManageMode = viewModel.isManageMode.value
        val searchQuery = viewModel.searchQuery.value
        val totalCount = viewModel.totalContactsCount.value

        if (isManageMode && searchQuery.isNotBlank() && totalCount > 0) {
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
