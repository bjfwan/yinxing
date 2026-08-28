package com.yinxing.launcher.feature.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.yinxing.launcher.common.ui.FontScaleActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterUsageEvents
import com.yinxing.launcher.common.lobster.LobsterPermissionTarget
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.common.ui.PageStateView
import com.yinxing.launcher.common.ui.AvatarEditorController

import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


private class ImportCandidateAdapter(
    private val candidates: List<PhoneImportCandidate>
) : RecyclerView.Adapter<ImportCandidateAdapter.ViewHolder>() {
    var onSelectionChanged: (() -> Unit)? = null

    private val checked = BooleanArray(candidates.size)

    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowCard: MaterialCardView = view.findViewById(R.id.card_import_contact)
        val checkBox: CheckBox = view.findViewById(R.id.cb_contact)
        val nameView: TextView = view.findViewById(R.id.tv_contact_name)
        val phoneView: TextView = view.findViewById(R.id.tv_contact_phone)
        val importedBadge: TextView = view.findViewById(R.id.tv_imported_badge)
    }

    override fun getItemId(position: Int): Long {
        val c = candidates[position]
        return c.name.hashCode().toLong().shl(32) xor (c.phone.hashCode().toLong() and 0xFFFFFFFFL)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_import_contact_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val candidate = candidates[position]
        holder.nameView.text = candidate.name
        holder.phoneView.text = candidate.phone
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = checked[position]
        if (candidate.alreadyImported) {
            holder.importedBadge.isVisible = true
            holder.checkBox.isEnabled = false
            holder.rowCard.alpha = 0.58f
            holder.rowCard.setOnClickListener(null)
        } else {
            holder.importedBadge.isVisible = false
            holder.checkBox.isEnabled = true
            holder.rowCard.alpha = 1f
            holder.rowCard.setOnClickListener {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
            }
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                checked[position] = isChecked
                onSelectionChanged?.invoke()

            }
        }
    }

    override fun getItemCount(): Int = candidates.size

    fun hasSelectableItems(): Boolean {
        return candidates.any { !it.alreadyImported }
    }

    fun hasUncheckedSelectable(): Boolean {
        return candidates.indices.any { index ->
            !candidates[index].alreadyImported && !checked[index]
        }
    }

    fun toggleAll() {
        val shouldSelectAll = hasUncheckedSelectable()
        candidates.indices.forEach { index ->
            if (!candidates[index].alreadyImported) {
                checked[index] = shouldSelectAll
            }
        }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged?.invoke()

    }

    fun selectedCandidates(): List<PhoneImportCandidate> {
        return buildList {
            candidates.indices.forEach { index ->
                if (!candidates[index].alreadyImported && checked[index]) {
                    add(candidates[index])
                }
            }
        }
    }
}

class PhoneContactActivity : FontScaleActivity() {
    companion object {
        private const val EXTRA_START_IN_MANAGE_MODE = "extra_start_in_manage_mode"

        fun createIntent(context: Context, startInManageMode: Boolean = false): Intent {
            return Intent(context, PhoneContactActivity::class.java)
                .putExtra(EXTRA_START_IN_MANAGE_MODE, startInManageMode)
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhoneContactAdapter
    private lateinit var stateView: PageStateView
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var viewModel: PhoneContactViewModel
    private lateinit var pageTitleText: TextView
    private lateinit var modeActionButton: View
    private lateinit var modeActionText: TextView
    private lateinit var modeSummaryText: TextView
    private lateinit var manageTools: View
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: View
    private lateinit var changeLayoutButton: View
    private lateinit var currentLayoutText: TextView
    private lateinit var layoutPreferences: PhoneContactLayoutPreferences
    private lateinit var avatarEditor: AvatarEditorController
    private var layoutStyle = PhoneContactLayoutStyle.LARGE

    private var launchedFromManageEntry = false
    private var dialogPhotoPreview: ImageView? = null
    private var dialogPhotoJob: Job? = null
    private var selectedAvatarUri: String? = null
    private var pendingCallContact: Contact? = null
    private var pendingCallTraceId: String? = null
    private var pendingContactsPermissionTraceId: String? = null
    private var searchInputUpdating = false


    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarEditor.edit(uri)
        }
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val contact = pendingCallContact
        val traceId = pendingCallTraceId ?: LobsterTrace.newId()
        pendingCallContact = null
        pendingCallTraceId = null
        if (granted && contact != null) {
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.permissionResult(LobsterPermissionTarget.PHONE, true)
                    .withTrace(traceId)
            )
            makeCall(contact, traceId)
        } else {
            LobsterClient.reportUsage(
                this,
                LobsterUsageEvents.CALL_PERMISSION_DENIED.withTrace(traceId)
            )
            showToast(getString(R.string.phone_call_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_contact)
        applySystemInsets()

        launcherPreferences = LauncherPreferences.getInstance(this)
        layoutPreferences = PhoneContactLayoutPreferences(this)
        avatarEditor = AvatarEditorController(this) { uri, bitmap ->
            selectedAvatarUri = uri.toString()
            dialogPhotoJob?.cancel()
            dialogPhotoPreview?.apply {
                imageTintList = null
                clearColorFilter()
                setPadding(0, 0, 0, 0)
                setImageBitmap(bitmap)
            }
        }
        layoutStyle = layoutPreferences.get()
        viewModel = ViewModelProvider(this, PhoneContactViewModel.Factory(this))[PhoneContactViewModel::class.java]

        recyclerView = findViewById(R.id.recycler_phone_contacts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        stateView = findViewById(R.id.view_page_state)
        stateView.attachContent(recyclerView)

        pageTitleText = findViewById(R.id.tv_page_title)
        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        modeSummaryText = findViewById(R.id.tv_mode_summary)

        manageTools = findViewById(R.id.layout_manage_tools)
        searchInput = findViewById(R.id.et_contact_search)
        clearSearchButton = findViewById(R.id.btn_clear_search)
        changeLayoutButton = findViewById(R.id.btn_change_layout)
        currentLayoutText = findViewById(R.id.tv_current_layout)

        adapter = PhoneContactAdapter(
            scope = lifecycleScope,
            onCallClick = ::makeCall,
            onEditClick = { contact -> showContactDialog(contact) }
        )
        adapter.setLayoutStyle(layoutStyle)
        recyclerView.adapter = adapter

        launchedFromManageEntry = intent.getBooleanExtra(EXTRA_START_IN_MANAGE_MODE, false)

        findViewById<MaterialCardView>(R.id.btn_back).setOnClickListener {
            if (viewModel.isManageMode.value && !launchedFromManageEntry) {
                viewModel.setManageMode(false)
            } else {
                finish()
            }
        }
        modeActionButton.setOnClickListener {
            if (viewModel.isManageMode.value) {
                showContactDialog(null)
            } else {
                viewModel.setManageMode(true)
            }
        }

        searchInput.doAfterTextChanged { editable ->
            if (searchInputUpdating) return@doAfterTextChanged
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
        }
        changeLayoutButton.setOnClickListener { showLayoutChoiceDialog() }
        renderCurrentLayout()
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
                        adapter.setManageMode(manageMode)
                        updateModeUi(manageMode)
                        updateState(adapter.currentList)
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
                        adapter.submitList(contacts)
                        updateState(contacts)
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun handleEvent(event: PhoneContactViewModel.Event) {
        when (event) {
            is PhoneContactViewModel.Event.LoadError ->
                showToast(getString(R.string.contact_load_failed, event.throwable.message.orEmpty()))
            is PhoneContactViewModel.Event.ContactAdded ->
                showToast(getString(R.string.contact_added_named, event.name))
            is PhoneContactViewModel.Event.ContactUpdated ->
                showToast(getString(R.string.contact_updated))
            is PhoneContactViewModel.Event.ContactDeleted ->
                showToast(getString(R.string.contact_deleted))
            is PhoneContactViewModel.Event.SaveError -> {
                val msg = if (event.isAdd) R.string.contact_add_failed else R.string.contact_update_failed
                showToast(getString(msg, event.throwable.message.orEmpty()))
            }
            is PhoneContactViewModel.Event.DeleteError ->
                showToast(getString(R.string.contact_delete_failed, event.throwable.message.orEmpty()))
            is PhoneContactViewModel.Event.ImportLoading ->
                showToast(getString(R.string.contacts_loading))
            is PhoneContactViewModel.Event.ImportCandidatesReady -> when {
                event.candidates.isEmpty() -> showToast(getString(R.string.contacts_empty))
                event.candidates.all { it.alreadyImported } ->
                    showToast(getString(R.string.contacts_all_imported))
                else -> showImportDialog(event.candidates)
            }
            is PhoneContactViewModel.Event.ImportCompleted ->
                showToast(getString(R.string.contacts_imported, event.count))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.setAnimationsEnabled(!launcherPreferences.isLowPerformanceModeEnabled())
        viewModel.refresh()
    }

    private fun updateModeUi(isManageMode: Boolean) {
        pageTitleText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_title else R.string.phone_contact_title
        )
        val actionText = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = actionText
        modeActionButton.contentDescription = actionText
        modeActionButton.isVisible = true
        modeSummaryText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_summary else R.string.phone_contact_call_summary_short
        )
        manageTools.isVisible = isManageMode
        clearSearchButton.isVisible = isManageMode && viewModel.searchQuery.value.isNotBlank()
        updateRecyclerLayout()
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
            ) { searchInput.text?.clear() }
            return
        }
        stateView.show(
            title = getString(R.string.state_phone_empty_title),
            message = getString(
                if (isManageMode) R.string.state_phone_manage_empty_message else R.string.state_phone_empty_message
            ),
            actionText = getString(
                if (isManageMode) R.string.state_phone_empty_action else R.string.action_manage_contacts
            )
        ) {
            if (isManageMode) showContactDialog(null) else viewModel.setManageMode(true)
        }
    }

    private fun updateRecyclerLayout() {
        val useGrid = layoutStyle == PhoneContactLayoutStyle.GRID
        val currentlyGrid = recyclerView.layoutManager is GridLayoutManager
        if (useGrid != currentlyGrid) {
            recyclerView.layoutManager = if (useGrid) {
                GridLayoutManager(this, 2)
            } else {
                LinearLayoutManager(this)
            }
        }
    }

    private fun showLayoutChoiceDialog() {
        val sheetView = layoutInflater.inflate(R.layout.dialog_phone_layout_choice, null)
        val dialog = LauncherDialogFactory.create(this, sheetView)

        val largeOption = sheetView.findViewById<MaterialCardView>(R.id.option_layout_large)
        val gridOption = sheetView.findViewById<MaterialCardView>(R.id.option_layout_grid)
        val largeBadge = sheetView.findViewById<TextView>(R.id.check_layout_large)
        val gridBadge = sheetView.findViewById<TextView>(R.id.check_layout_grid)
        val selectedStroke = (3 * resources.displayMetrics.density).toInt()

        fun renderSelection() {
            val largeSelected = layoutStyle == PhoneContactLayoutStyle.LARGE
            largeOption.isSelected = largeSelected
            gridOption.isSelected = !largeSelected
            largeBadge.visibility = if (largeSelected) View.VISIBLE else View.INVISIBLE
            gridBadge.visibility = if (largeSelected) View.INVISIBLE else View.VISIBLE
            largeOption.strokeWidth = if (largeSelected) selectedStroke else 1
            gridOption.strokeWidth = if (largeSelected) 1 else selectedStroke
            largeOption.strokeColor = ContextCompat.getColor(
                this,
                if (largeSelected) R.color.launcher_primary else R.color.launcher_outline
            )
            gridOption.strokeColor = ContextCompat.getColor(
                this,
                if (largeSelected) R.color.launcher_outline else R.color.launcher_primary
            )
        }

        fun select(style: PhoneContactLayoutStyle) {
            setLayoutStyle(style)
            renderSelection()
            dialog.dismiss()
        }

        largeOption.setOnClickListener { select(PhoneContactLayoutStyle.LARGE) }
        gridOption.setOnClickListener { select(PhoneContactLayoutStyle.GRID) }
        sheetView.findViewById<View>(R.id.btn_layout_cancel).setOnClickListener { dialog.dismiss() }
        renderSelection()
        dialog.show()
    }

    private fun setLayoutStyle(style: PhoneContactLayoutStyle) {
        if (layoutStyle == style) return
        layoutStyle = style
        layoutPreferences.set(style)
        adapter.setLayoutStyle(style)
        updateRecyclerLayout()
        renderCurrentLayout()
    }

    private fun renderCurrentLayout() {
        currentLayoutText.setText(
            if (layoutStyle == PhoneContactLayoutStyle.LARGE) {
                R.string.phone_contact_layout_large_summary
            } else {
                R.string.phone_contact_layout_grid_summary
            }
        )
    }

    private fun makeCall(contact: Contact, traceId: String = LobsterTrace.newId()) {
        val number = contact.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallContact = contact
            pendingCallTraceId = traceId
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.permissionRequested(LobsterPermissionTarget.PHONE)
                    .withTrace(traceId)
            )
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
        val startedAt = SystemClock.elapsedRealtime()
        runCatching { startActivity(intent) }.onFailure {
            LobsterClient.reportUsage(this, LobsterUsageEvents.OUTGOING_CALL_FAILED.withTrace(traceId))
            showToast(getString(R.string.dial_failed, it.message ?: ""))
        }.onSuccess {
            LobsterClient.reportUsage(this, LobsterUsageEvents.OUTGOING_CALL_STARTED.withTrace(traceId))
            viewModel.incrementCallCountAsync(contact.id)
        }
        LobsterClient.reportMetrics(
            this,
            listOf(LauncherTraceNames.PHONE_PLACE_CALL to (SystemClock.elapsedRealtime() - startedAt)),
            traceId
        )
    }

    private fun showImportFromContacts() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val traceId = LobsterTrace.newId()
            pendingContactsPermissionTraceId = traceId
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.permissionRequested(LobsterPermissionTarget.CONTACTS)
                    .withTrace(traceId)
            )
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            return
        }
        viewModel.loadImportCandidates()
    }

    private fun showImportDialog(candidates: List<PhoneImportCandidate>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_contacts, null)
        val dialog = LauncherDialogFactory.create(this, dialogView, dismissOnTouchOutside = false)

        val listView = dialogView.findViewById<RecyclerView>(R.id.layout_import_list)
        val toggleButton = dialogView.findViewById<MaterialCardView>(R.id.btn_toggle_select)
        val toggleLabel = dialogView.findViewById<TextView>(R.id.tv_toggle_select)
        val cancelButton = dialogView.findViewById<MaterialCardView>(R.id.btn_cancel_import)
        val importButton = dialogView.findViewById<MaterialCardView>(R.id.btn_import)
        val selectionAdapter = ImportCandidateAdapter(candidates)

        selectionAdapter.onSelectionChanged = {
            toggleLabel.text = getString(
                if (selectionAdapter.hasUncheckedSelectable()) {
                    R.string.action_select_all
                } else {
                    R.string.action_deselect_all
                }
            )
            toggleButton.isVisible = selectionAdapter.hasSelectableItems()
        }

        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = selectionAdapter
        selectionAdapter.onSelectionChanged?.invoke()


        toggleButton.setOnClickListener {
            selectionAdapter.toggleAll()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        importButton.setOnClickListener {
            val selected = selectionAdapter.selectedCandidates()
            if (selected.isEmpty()) {
                showToast(getString(R.string.contacts_select_required))
                return@setOnClickListener
            }
            dialog.dismiss()
            viewModel.importContacts(selected)
        }

        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            val traceId = pendingContactsPermissionTraceId ?: LobsterTrace.newId()
            pendingContactsPermissionTraceId = null
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.permissionResult(LobsterPermissionTarget.CONTACTS, granted)
                    .withTrace(traceId)
            )
            if (granted) showImportFromContacts()
        }
    }

    private fun showContactDialog(initial: Contact?) {
        selectedAvatarUri = initial?.avatarUri

        val dialogView = layoutInflater.inflate(R.layout.dialog_phone_contact, null)
        val dialog = LauncherDialogFactory.create(this, dialogView, dismissOnTouchOutside = false)

        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = getString(
            if (initial == null) R.string.phone_contact_dialog_add_title else R.string.phone_contact_dialog_edit_title
        )

        val nameField = dialogView.findViewById<EditText>(R.id.et_name)
        val phoneField = dialogView.findViewById<EditText>(R.id.et_phone)
        val autoAnswerSwitch = dialogView.findViewById<SwitchCompat>(R.id.sw_auto_answer)
        val pickContactButton = dialogView.findViewById<MaterialCardView>(R.id.btn_pick_contact)
        val selectPhotoButton = dialogView.findViewById<MaterialCardView>(R.id.btn_select_photo)
        val cancelButton = dialogView.findViewById<MaterialCardView>(R.id.btn_cancel)
        val confirmButton = dialogView.findViewById<MaterialCardView>(R.id.btn_confirm)
        val cancelLabel = dialogView.findViewById<TextView>(R.id.btn_cancel_label)
        dialogPhotoPreview = dialogView.findViewById(R.id.iv_photo_preview)

        nameField.setText(initial?.name.orEmpty())
        phoneField.setText(initial?.phoneNumber.orEmpty())
        autoAnswerSwitch.isChecked = initial?.autoAnswer ?: false
        renderDialogPhoto()

        pickContactButton.setOnClickListener {
            dialog.dismiss()
            showImportFromContacts()
        }
        selectPhotoButton.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        if (initial != null) {
            cancelLabel.text = getString(R.string.action_delete)
            cancelLabel.setTextColor(ContextCompat.getColor(this, R.color.launcher_danger))
            cancelButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.launcher_danger_soft))
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            if (initial != null) showDeleteDialog(initial)
        }

        confirmButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            if (name.isEmpty()) {
                showToast(getString(R.string.input_contact_name))
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                showToast(getString(R.string.contact_phone_required_simple))
                return@setOnClickListener
            }
            viewModel.pendingAvatarUri = selectedAvatarUri
            viewModel.saveContact(initial, name, phone, autoAnswerSwitch.isChecked)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            dialogPhotoJob?.cancel()
            dialogPhotoPreview = null
            selectedAvatarUri = null
        }

        dialog.show()
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.phone_contact_root)
        val baseTop = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top + baseTop, bottom = bars.bottom)
            insets
        }
    }

    private fun showDeleteDialog(contact: Contact) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_contact, null)
        val dialog = LauncherDialogFactory.create(this, dialogView, dismissOnTouchOutside = false)
        dialogView.findViewById<TextView>(R.id.tv_delete_message).text =
            getString(R.string.video_contact_delete_message, contact.name)
        dialogView.findViewById<MaterialCardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<MaterialCardView>(R.id.btn_delete)
            .setOnClickListener {
                viewModel.deleteContact(contact)
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun renderDialogPhoto() {
        val preview = dialogPhotoPreview ?: return
        val uri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        dialogPhotoJob?.cancel()
        if (uri == null) {
            val padding = (28 * resources.displayMetrics.density).toInt()
            preview.setPadding(padding, padding, padding, padding)
            preview.setImageResource(R.drawable.ic_contact_avatar_placeholder)
            preview.clearColorFilter()
            return
        }
        val padding = (28 * resources.displayMetrics.density).toInt()
        preview.setImageResource(R.drawable.ic_contact_avatar_placeholder)
        preview.clearColorFilter()
        preview.setPadding(padding, padding, padding, padding)
        dialogPhotoJob = lifecycleScope.launch {
            val bitmap = runCatching {
                MediaThumbnailLoader.loadBitmap(this@PhoneContactActivity, Uri.parse(uri), 480, 480)
            }.getOrNull()
            if (dialogPhotoPreview === preview && selectedAvatarUri == uri && bitmap != null) {
                preview.setPadding(0, 0, 0, 0)
                preview.setImageBitmap(bitmap)
            }
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
