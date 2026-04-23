package com.yinxing.launcher.feature.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.PageStateView
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactAvatarStore
import com.yinxing.launcher.data.contact.ContactStorage
import com.google.android.material.card.MaterialCardView
import java.util.UUID

class PhoneContactActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_START_IN_MANAGE_MODE = "extra_start_in_manage_mode"

        fun createIntent(context: Context, startInManageMode: Boolean = false): Intent {
            return Intent(context, PhoneContactActivity::class.java)
                .putExtra(EXTRA_START_IN_MANAGE_MODE, startInManageMode)
        }
    }

    private data class ImportCandidate(
        val name: String,
        val phone: String,
        val alreadyImported: Boolean
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhoneContactAdapter
    private lateinit var stateView: PageStateView
    private lateinit var manager: PhoneContactManager
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
    private var selectedAvatarUri: String? = null
    private var pendingCallContact: Contact? = null

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
            Toast.makeText(this, getString(R.string.phone_call_permission_required), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_contact)

        manager = PhoneContactManager.getInstance(this)

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
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
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

    private fun loadContacts() {
        allContacts = manager.getContacts()
        renderContacts()
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
            Toast.makeText(this, getString(R.string.dial_failed, it.message ?: ""), Toast.LENGTH_SHORT)
                .show()
        }.onSuccess {
            manager.incrementCallCount(contact.id)
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
            val seen = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim() ?: continue
                val phone = cursor.getString(phoneIdx)?.trim() ?: continue
                if (name.isBlank() || phone.isBlank()) continue
                val key = "${name}_${phone.filter { it.isDigit() }}"
                if (seen.add(key)) {
                    entries += name to phone
                }
            }
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, getString(R.string.contacts_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val existingPhones = manager.getContacts()
            .mapNotNull { it.phoneNumber?.filter(Char::isDigit) }
            .toSet()
        val candidates = entries.map { (name, phone) ->
            ImportCandidate(
                name = name,
                phone = phone,
                alreadyImported = existingPhones.contains(phone.filter(Char::isDigit))
            )
        }

        if (candidates.all { it.alreadyImported }) {
            Toast.makeText(this, getString(R.string.contacts_all_imported), Toast.LENGTH_SHORT).show()
            return
        }

        val checked = BooleanArray(candidates.size)
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_contacts, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val listContainer = dialogView.findViewById<LinearLayout>(R.id.layout_import_list)
        val toggleButton = dialogView.findViewById<MaterialCardView>(R.id.btn_toggle_select)
        val toggleLabel = dialogView.findViewById<TextView>(R.id.tv_toggle_select)
        val cancelButton = dialogView.findViewById<MaterialCardView>(R.id.btn_cancel_import)
        val importButton = dialogView.findViewById<MaterialCardView>(R.id.btn_import)
        val selectableCheckBoxes = mutableListOf<CheckBox>()

        fun updateToggleLabel() {
            toggleLabel.text = getString(
                if (selectableCheckBoxes.any { !it.isChecked }) {
                    R.string.action_select_all
                } else {
                    R.string.action_deselect_all
                }
            )
            toggleButton.isVisible = selectableCheckBoxes.isNotEmpty()
        }

        candidates.forEachIndexed { index, candidate ->
            val itemView = layoutInflater.inflate(R.layout.item_import_contact_option, listContainer, false)
            val rowCard = itemView.findViewById<MaterialCardView>(R.id.card_import_contact)
            val checkBox = itemView.findViewById<CheckBox>(R.id.cb_contact)
            val nameView = itemView.findViewById<TextView>(R.id.tv_contact_name)
            val phoneView = itemView.findViewById<TextView>(R.id.tv_contact_phone)
            val importedBadge = itemView.findViewById<TextView>(R.id.tv_imported_badge)

            nameView.text = candidate.name
            phoneView.text = candidate.phone

            if (candidate.alreadyImported) {
                importedBadge.isVisible = true
                checkBox.isChecked = false
                checkBox.isEnabled = false
                rowCard.alpha = 0.58f
            } else {
                selectableCheckBoxes += checkBox
                rowCard.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    checked[index] = isChecked
                    updateToggleLabel()
                }
            }

            listContainer.addView(itemView)
        }

        toggleButton.setOnClickListener {
            val shouldSelectAll = selectableCheckBoxes.any { !it.isChecked }
            selectableCheckBoxes.forEach { it.isChecked = shouldSelectAll }
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        importButton.setOnClickListener {
            var count = 0
            candidates.forEachIndexed { index, candidate ->
                if (!candidate.alreadyImported && checked[index]) {
                    manager.addContact(
                        Contact(
                            id = UUID.randomUUID().toString(),
                            name = candidate.name,
                            phoneNumber = candidate.phone,
                            preferredAction = Contact.PreferredAction.PHONE
                        )
                    )
                    count++
                }
            }
            dialog.dismiss()
            if (count > 0) {
                loadContacts()
                Toast.makeText(this, getString(R.string.contacts_imported, count), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        updateToggleLabel()
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
                Toast.makeText(this, getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                Toast.makeText(this, getString(R.string.contact_phone_required_simple), Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            saveContact(initial, name, phone, autoAnswerSwitch.isChecked)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
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
                ContactAvatarStore.deleteOwnedAvatar(this, contact.avatarUri)
                manager.removeContact(contact.id)
                loadContacts()
                Toast.makeText(this, getString(R.string.contact_deleted), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun saveContact(original: Contact?, name: String, phone: String, autoAnswer: Boolean) {
        val contactId = original?.id ?: UUID.randomUUID().toString()
        val resolvedAvatar = resolveAvatar(contactId, original?.avatarUri, selectedAvatarUri)
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
            Toast.makeText(this, getString(R.string.contact_added_named, name), Toast.LENGTH_SHORT).show()
        } else {
            manager.updateContact(contact)
            Toast.makeText(this, getString(R.string.contact_updated), Toast.LENGTH_SHORT).show()
        }
        loadContacts()
    }

    private fun resolveAvatar(contactId: String, previous: String?, selected: String?): String? {
        if (selected.isNullOrBlank()) return previous
        if (selected == previous) return previous
        val saved = ContactAvatarStore.saveFromUri(this, Uri.parse(selected), contactId) ?: return previous
        if (!previous.isNullOrBlank() && previous != saved) {
            ContactAvatarStore.deleteOwnedAvatar(this, previous)
        }
        return saved
    }

    private fun renderDialogPhoto() {
        val preview = dialogPhotoPreview ?: return
        val uri = selectedAvatarUri?.takeIf { it.isNotBlank() }
        if (uri == null) {
            val padding = (28 * resources.displayMetrics.density).toInt()
            preview.setPadding(padding, padding, padding, padding)
            preview.setImageResource(android.R.drawable.ic_menu_camera)
        } else {
            preview.setPadding(0, 0, 0, 0)
            preview.setImageURI(null)
            preview.setImageURI(Uri.parse(uri))
        }
    }
}
