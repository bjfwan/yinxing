package com.yinxing.launcher.feature.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.common.ui.PageStateView

import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactAvatarStore
import com.yinxing.launcher.data.contact.ContactStorage
import com.yinxing.launcher.data.home.LauncherPreferences
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private data class ImportCandidate(
    val name: String,
    val phone: String,
    val alreadyImported: Boolean
)

private class ImportCandidateAdapter(
    private val candidates: List<ImportCandidate>
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

    fun selectedCandidates(): List<ImportCandidate> {
        return buildList {
            candidates.indices.forEach { index ->
                if (!candidates[index].alreadyImported && checked[index]) {
                    add(candidates[index])
                }
            }
        }
    }
}

class PhoneContactActivity : AppCompatActivity() {
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
    private lateinit var manager: PhoneContactManager
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var pageTitleText: TextView
    private lateinit var modeActionButton: MaterialCardView
    private lateinit var modeActionText: TextView
    private lateinit var modeSummaryText: TextView
    private lateinit var searchLayout: MaterialCardView
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: MaterialCardView

    private var launchedFromManageEntry = false
    private var isManageMode = false
    private var searchQuery = ""
    private var allContacts: List<Contact> = emptyList()
    private var dialogPhotoPreview: ImageView? = null
    private var dialogPhotoJob: Job? = null
    private var selectedAvatarUri: String? = null
    private var pendingCallContact: Contact? = null
    private var loadJob: Job? = null
    private var importJob: Job? = null


    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri.toString()
            renderDialogPhoto()
        }
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val contact = pendingCallContact
        pendingCallContact = null
        if (granted && contact != null) {
            makeCall(contact)
        } else {
            showToast(getString(R.string.phone_call_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_contact)

        manager = PhoneContactManager.getInstance(this)
        launcherPreferences = LauncherPreferences.getInstance(this)

        recyclerView = findViewById(R.id.recycler_phone_contacts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        stateView = findViewById(R.id.view_page_state)
        stateView.attachContent(recyclerView)

        pageTitleText = findViewById(R.id.tv_page_title)
        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        modeSummaryText = findViewById(R.id.tv_mode_summary)

        searchLayout = findViewById(R.id.layout_manage_search)
        searchInput = findViewById(R.id.et_contact_search)
        clearSearchButton = findViewById(R.id.btn_clear_search)

        adapter = PhoneContactAdapter(
            scope = lifecycleScope,
            onCallClick = { contact -> makeCall(contact) },
            onEditClick = { contact -> showContactDialog(contact) }
        )
        recyclerView.adapter = adapter

        launchedFromManageEntry = intent.getBooleanExtra(EXTRA_START_IN_MANAGE_MODE, false)

        findViewById<MaterialCardView>(R.id.btn_back).setOnClickListener {
            if (isManageMode && !launchedFromManageEntry) {
                switchToCallMode()
            } else {
                finish()
            }
        }
        modeActionButton.setOnClickListener {
            if (isManageMode) {
                showContactDialog(null)
            } else {
                switchToManageMode()
            }
        }

        searchInput.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty()
            clearSearchButton.isVisible = isManageMode && searchQuery.isNotBlank()
            renderContacts()
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
        }

        if (launchedFromManageEntry) {
            switchToManageMode()
        } else {
            updateModeUi()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.setFullCardTapEnabled(launcherPreferences.isFullCardTapEnabled())
        adapter.setAnimationsEnabled(!launcherPreferences.isLowPerformanceModeEnabled())
        refreshContacts()
    }

    private fun switchToManageMode() {
        isManageMode = true
        adapter.setManageMode(true)
        updateModeUi()
        renderContacts()
    }

    private fun switchToCallMode() {
        isManageMode = false
        adapter.setManageMode(false)
        updateModeUi()
        if (searchQuery.isNotBlank()) {
            searchInput.text?.clear()
        } else {
            renderContacts()
        }
    }

    private fun updateModeUi() {
        pageTitleText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_title else R.string.phone_contact_title
        )
        val actionText = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = actionText
        modeActionButton.contentDescription = actionText
        modeActionButton.isVisible = true
        modeSummaryText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_summary else R.string.phone_contact_call_summary
        )
        searchLayout.isVisible = isManageMode
        clearSearchButton.isVisible = isManageMode && searchQuery.isNotBlank()
    }

    private fun refreshContacts() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val contacts = withContext(Dispatchers.IO) { manager.getContacts() }
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
        adapter.submitList(contacts)
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
            ) { searchInput.text?.clear() }
            return
        }
        stateView.show(
            title = getString(R.string.state_phone_empty_title),
            message = getString(
                if (isManageMode) R.string.state_phone_manage_empty_message else R.string.state_phone_empty_message
            ),
            actionText = getString(
                if (isManageMode) R.string.state_phone_empty_action else R.string.action_back_home
            )
        ) {
            if (isManageMode) showContactDialog(null) else finish()
        }
    }

    private fun makeCall(contact: Contact) {
        val number = contact.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallContact = contact
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
        runCatching { startActivity(intent) }.onFailure {
            showToast(getString(R.string.dial_failed, it.message ?: ""))
        }.onSuccess {
            lifecycleScope.launch(Dispatchers.Default) {
                runCatching { manager.incrementCallCount(contact.id) }
            }
        }
    }

    private fun showImportFromContacts() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            return
        }
        importJob?.cancel()
        showToast(getString(R.string.contacts_loading))
        importJob = lifecycleScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) { loadImportCandidates() }
                when {
                    candidates.isEmpty() -> showToast(getString(R.string.contacts_empty))
                    candidates.all { it.alreadyImported } -> {
                        showToast(getString(R.string.contacts_all_imported))
                    }
                    else -> showImportDialog(candidates)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showToast(getString(R.string.contact_load_failed, throwable.message.orEmpty()))
            }
        }
    }

    private fun loadImportCandidates(): List<ImportCandidate> {
        val entries = mutableListOf<Pair<String, String>>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = hashSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim() ?: continue
                val phone = cursor.getString(phoneIdx)?.trim() ?: continue
                if (name.isBlank() || phone.isBlank()) continue
                val digits = phone.filter(Char::isDigit)
                if (digits.isEmpty()) continue
                if (seen.add("${name}_$digits")) {

                    entries += name to phone
                }
            }
        }
        if (entries.isEmpty()) {
            return emptyList()
        }
        val existingPhones = manager.getContacts()
            .mapNotNull { it.phoneNumber?.filter(Char::isDigit) }
            .toHashSet()
        return entries.map { (name, phone) ->
            ImportCandidate(
                name = name,
                phone = phone,
                alreadyImported = phone.filter(Char::isDigit) in existingPhones
            )
        }
    }

    private fun showImportDialog(candidates: List<ImportCandidate>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_contacts, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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

            lifecycleScope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val contacts = selected.map { candidate ->
                            Contact(
                                id = UUID.randomUUID().toString(),
                                name = candidate.name,
                                phoneNumber = candidate.phone,
                                preferredAction = Contact.PreferredAction.PHONE
                            )
                        }
                        manager.addContacts(contacts)
                        contacts.size
                    }
                    refreshContacts()
                    showToast(getString(R.string.contacts_imported, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    showToast(getString(R.string.contact_add_failed, throwable.message.orEmpty()))
                }
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            showImportFromContacts()
        }
    }

    private fun showContactDialog(initial: Contact?) {
        selectedAvatarUri = initial?.avatarUri

        val dialogView = layoutInflater.inflate(R.layout.dialog_phone_contact, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
            saveContact(initial, name, phone, autoAnswerSwitch.isChecked)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            dialogPhotoJob?.cancel()
            dialogPhotoPreview = null
            selectedAvatarUri = null
        }

        dialog.show()
    }

    private fun showDeleteDialog(contact: Contact) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_contact, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tv_delete_message).text =
            getString(R.string.video_contact_delete_message, contact.name)
        dialogView.findViewById<MaterialCardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<MaterialCardView>(R.id.btn_delete)
            .setOnClickListener {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ContactAvatarStore.deleteOwnedAvatar(this@PhoneContactActivity, contact.avatarUri)
                            manager.removeContact(contact.id)
                        }
                        refreshContacts()
                        showToast(getString(R.string.contact_deleted))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        showToast(getString(R.string.contact_delete_failed, throwable.message.orEmpty()))
                    }
                }
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun saveContact(original: Contact?, name: String, phone: String, autoAnswer: Boolean) {
        lifecycleScope.launch {
            val action = if (original == null) R.string.contact_added_named else R.string.contact_updated
            val failure = if (original == null) R.string.contact_add_failed else R.string.contact_update_failed
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    val previousAvatar = original?.avatarUri
                    val selectedAvatar = selectedAvatarUri
                    val contactId = original?.id ?: UUID.randomUUID().toString()
                    var createdAvatar: String? = null
                    val resolvedAvatar = when {
                        selectedAvatar.isNullOrBlank() -> previousAvatar
                        selectedAvatar == previousAvatar -> previousAvatar
                        else -> ContactAvatarStore.saveFromUri(this@PhoneContactActivity, Uri.parse(selectedAvatar), contactId)
                            ?.also { if (it != previousAvatar) createdAvatar = it }
                            ?: previousAvatar
                    }
                    try {
                        val contact = Contact(
                            id = contactId,
                            name = name,
                            phoneNumber = phone,
                            avatarUri = resolvedAvatar,
                            preferredAction = Contact.PreferredAction.PHONE,
                            isPinned = original?.isPinned ?: false,
                            callCount = original?.callCount ?: 0,
                            lastCallTime = original?.lastCallTime ?: 0,
                            autoAnswer = autoAnswer
                        )
                        if (original == null) {
                            manager.addContact(contact)
                        } else {
                            manager.updateContact(contact)
                        }
                        if (!previousAvatar.isNullOrBlank() && previousAvatar != resolvedAvatar) {
                            ContactAvatarStore.deleteOwnedAvatar(this@PhoneContactActivity, previousAvatar)
                        }
                    } catch (throwable: Throwable) {
                        if (!createdAvatar.isNullOrBlank()) {
                            ContactAvatarStore.deleteOwnedAvatar(this@PhoneContactActivity, createdAvatar)
                        }
                        throw throwable
                    }
                }
                refreshContacts()
                if (original == null) {
                    showToast(getString(action, name))
                } else {
                    showToast(getString(action))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showToast(getString(failure, throwable.message.orEmpty()))
            }
        }
    }


    private fun renderDialogPhoto() {
        val preview = dialogPhotoPreview ?: return
        val uri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        dialogPhotoJob?.cancel()
        if (uri == null) {
            val padding = (28 * resources.displayMetrics.density).toInt()
            preview.setPadding(padding, padding, padding, padding)
            preview.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }
        val padding = (28 * resources.displayMetrics.density).toInt()
        preview.setImageResource(android.R.drawable.ic_menu_camera)
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
