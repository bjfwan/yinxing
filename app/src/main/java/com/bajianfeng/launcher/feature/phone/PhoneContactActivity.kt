package com.bajianfeng.launcher.feature.phone

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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactAvatarStore
import com.bajianfeng.launcher.data.contact.ContactStorage
import java.util.UUID

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
    private lateinit var pageTitleText: TextView
    private lateinit var modeActionButton: CardView
    private lateinit var modeActionText: TextView
    private lateinit var modeSummaryText: TextView

    private lateinit var searchLayout: CardView
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: TextView

    private var launchedFromManageEntry = false
    private var isManageMode = false
    private var searchQuery = ""

    private var allContacts: List<Contact> = emptyList()

    private var dialogPhotoPreview: ImageView? = null
    private var selectedAvatarUri: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri.toString()
            renderDialogPhoto()
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

        findViewById<CardView>(R.id.btn_back).setOnClickListener {
            if (isManageMode && !launchedFromManageEntry) switchToCallMode() else finish()
        }
        modeActionButton.setOnClickListener {
            if (isManageMode) showContactDialog(null)
        }

        searchInput.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty()
            clearSearchButton.isVisible = searchQuery.isNotBlank()
            renderContacts()
        }
        clearSearchButton.setOnClickListener { searchInput.text?.clear() }

        updateModeUi()
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
        if (searchQuery.isNotBlank()) searchInput.text?.clear() else renderContacts()
        updateModeUi()
    }

    private fun updateModeUi() {
        pageTitleText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_title else R.string.phone_contact_title
        )
        modeActionButton.isVisible = isManageMode
        modeActionText.text = getString(R.string.action_add)
        modeSummaryText.text = getString(
            if (isManageMode) R.string.phone_contact_manage_summary else R.string.phone_contact_call_summary
        )
        searchLayout.isVisible = isManageMode
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
        if (contacts.isNotEmpty()) { stateView.hide(); return }
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
            message = getString(if (isManageMode) R.string.state_phone_manage_empty_message else R.string.state_phone_empty_message),
            actionText = getString(if (isManageMode) R.string.state_phone_empty_action else R.string.action_back_home)
        ) {
            if (isManageMode) showContactDialog(null) else finish()
        }

    }

    private fun makeCall(contact: Contact) {
        val number = contact.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        val intent = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, getString(R.string.dial_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
        }
        manager.incrementCallCount(contact.id)
    }

    // ───────────────────────────── 批量导入通讯录 ─────────────────────────────

    private fun showImportFromContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            return
        }

        val entries = mutableListOf<Pair<String, String>>() // name to phone
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
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
                if (seen.add(key)) entries.add(name to phone)
            }
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, getString(R.string.contacts_empty), Toast.LENGTH_SHORT).show()
            return
        }

        // 已存在的电话号码集合（只保留数字，用于去重比对）
        val existingPhones = manager.getContacts()
            .mapNotNull { it.phoneNumber?.filter(Char::isDigit) }
            .toSet()
        // 标记每个条目是否已导入
        val alreadyImported = BooleanArray(entries.size) { i ->
            existingPhones.contains(entries[i].second.filter(Char::isDigit))
        }

        val checked = BooleanArray(entries.size) { false }
        val dp = resources.displayMetrics.density

        // ── 外层容器：标题栏 + 列表 ──
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 顶部标题
        val titleView = TextView(this).apply {
            text = getString(R.string.action_import_from_contacts)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_text_primary))
            val hPad = (20 * dp).toInt()
            val vPad = (18 * dp).toInt()
            setPadding(hPad, vPad, hPad, (10 * dp).toInt())
        }
        rootLayout.addView(titleView)

        // 分割线
        val dividerTop = android.view.View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_outline))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        }
        rootLayout.addView(dividerTop)

        // 联系人列表
        val scrollView = ScrollView(this).apply {
            val maxHeightPx = (resources.displayMetrics.heightPixels * 0.55f).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxHeightPx)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = (8 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }

        entries.forEachIndexed { index, (name, phone) ->
            val imported = alreadyImported[index]

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val hPad = (12 * dp).toInt()
                val vPad = (6 * dp).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                if (!imported) {
                    background = ContextCompat.getDrawable(this@PhoneContactActivity,
                        android.R.drawable.list_selector_background)
                    isClickable = true
                    isFocusable = true
                }
                alpha = if (imported) 0.4f else 1f
            }

            val checkBox = CheckBox(this).apply {
                isChecked = false
                isFocusable = false
                isClickable = false
                isEnabled = !imported
                scaleX = 1.4f
                scaleY = 1.4f
                val margin = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = margin }
                if (!imported) {
                    setOnCheckedChangeListener { _, isChecked -> checked[index] = isChecked }
                }
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameView = TextView(this).apply {
                text = name
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_text_primary))
            }
            val phoneView = TextView(this).apply {
                text = phone
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (3 * dp).toInt() }
            }
            textLayout.addView(nameView)
            textLayout.addView(phoneView)

            // 已导入标签
            if (imported) {
                val importedTag = TextView(this).apply {
                    text = getString(R.string.action_import) + "过"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_text_secondary))
                    background = ContextCompat.getDrawable(this@PhoneContactActivity, R.drawable.edit_text_background)
                    val tagH = (4 * dp).toInt()
                    val tagV = (2 * dp).toInt()
                    setPadding(tagH * 2, tagV, tagH * 2, tagV)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                itemLayout.addView(checkBox)
                itemLayout.addView(textLayout)
                itemLayout.addView(importedTag)
            } else {
                itemLayout.addView(checkBox)
                itemLayout.addView(textLayout)
                itemLayout.setOnClickListener {
                    checkBox.isChecked = !checkBox.isChecked
                    checked[index] = checkBox.isChecked
                }
            }
            container.addView(itemLayout)

            // 条目间分割线
            if (index < entries.size - 1) {
                val divider = android.view.View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_outline))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    lp.marginStart = (20 * dp).toInt()
                    lp.marginEnd = (20 * dp).toInt()
                    layoutParams = lp
                }
                container.addView(divider)
            }
        }
        scrollView.addView(container)
        rootLayout.addView(scrollView)

        // 底部分割线
        val dividerBottom = android.view.View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_outline))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        }
        rootLayout.addView(dividerBottom)

        // 底部按钮栏
        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val pad = (8 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        fun makeButton(text: String, colorRes: Int, weight: Float): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@PhoneContactActivity, colorRes))
                gravity = android.view.Gravity.CENTER
                val pad = (14 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(this@PhoneContactActivity,
                    android.R.drawable.list_selector_background)
            }
        }

        val btnSelectAll = makeButton(getString(R.string.action_select_all), R.color.launcher_primary, 1f)
        val btnCancel = makeButton(getString(R.string.action_cancel), R.color.launcher_text_secondary, 1f)
        val btnImport = makeButton(getString(R.string.action_import), R.color.launcher_primary, 1f)

        buttonBar.addView(btnSelectAll)
        buttonBar.addView(btnCancel)
        buttonBar.addView(btnImport)
        rootLayout.addView(buttonBar)

        // 给 rootLayout 设置白色圆角背景
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = (16 * dp)
            setColor(ContextCompat.getColor(this@PhoneContactActivity, R.color.launcher_surface))
        }
        rootLayout.background = bgDrawable
        rootLayout.clipToOutline = true

        val dialog = AlertDialog.Builder(this)
            .setView(rootLayout)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 全选 / 取消全选 —— 不关闭弹窗，跳过已导入条目
        var allSelected = false
        btnSelectAll.setOnClickListener {
            allSelected = !allSelected
            // 只对未导入的条目操作
            entries.indices.forEach { i ->
                if (!alreadyImported[i]) checked[i] = allSelected
            }
            var checkboxVisitIdx = 0
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is LinearLayout) {
                    // 找到对应 entries 的真实索引（跳过分割线View）
                    val cb = child.getChildAt(0) as? CheckBox
                    if (cb != null && cb.isEnabled) {
                        cb.isChecked = allSelected
                    }
                    checkboxVisitIdx++
                }
            }
            btnSelectAll.text = getString(
                if (allSelected) R.string.action_deselect_all else R.string.action_select_all
            )
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnImport.setOnClickListener {
            var count = 0
            entries.forEachIndexed { index, (name, phone) ->
                if (checked[index]) {
                    manager.addContact(
                        Contact(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            phoneNumber = phone,
                            preferredAction = Contact.PreferredAction.PHONE
                        )
                    )
                    count++
                }
            }
            dialog.dismiss()
            if (count > 0) {
                loadContacts()
                Toast.makeText(this, getString(R.string.contacts_imported, count), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        // 让对话框宽度接近全屏
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            showImportFromContacts()
        }
    }

    // ───────────────────────────── 单个联系人对话框 ─────────────────────────────

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
        dialogPhotoPreview = dialogView.findViewById(R.id.iv_photo_preview)

        nameField.setText(initial?.name.orEmpty())
        phoneField.setText(initial?.phoneNumber.orEmpty())
        autoAnswerSwitch.isChecked = initial?.autoAnswer ?: false
        renderDialogPhoto()

        dialogView.findViewById<CardView>(R.id.btn_pick_contact).setOnClickListener {
            dialog.dismiss()
            showImportFromContacts()
        }
        dialogView.findViewById<CardView>(R.id.btn_select_photo).setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val cancelLabel = dialogView.findViewById<TextView>(R.id.btn_cancel_label)
        if (initial != null) {
            cancelLabel.text = getString(R.string.action_delete)
            cancelLabel.setTextColor(getColor(android.R.color.holo_red_dark))
        }
        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            if (initial != null) showDeleteDialog(initial)
        }

        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = nameField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                Toast.makeText(this, getString(R.string.contact_phone_required_simple), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveContact(initial, name, phone, autoAnswerSwitch.isChecked)
            dialog.dismiss()
        }

        dialog.setOnDismissListener { dialogPhotoPreview = null }
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
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btn_delete)
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
        if (!previous.isNullOrBlank() && previous != saved) ContactAvatarStore.deleteOwnedAvatar(this, previous)
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
