package com.bajianfeng.launcher.feature.videocall

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.automation.wechat.service.WeChatAccessibilityService
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.util.NetworkUtil
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager
import java.util.UUID

class VideoCallActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoCallContactAdapter
    private lateinit var manageAdapter: ContactManageAdapter
    private lateinit var modeActionButton: CardView
    private lateinit var modeActionText: TextView
    private lateinit var stateLayout: android.view.View
    private lateinit var stateTitle: TextView
    private lateinit var stateMessage: TextView
    private lateinit var stateActionButton: CardView
    private lateinit var stateActionText: TextView
    private val contactList = mutableListOf<Contact>()
    private lateinit var ttsService: TTSService
    private lateinit var contactManager: ContactManager
    private var isManageMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        ttsService = TTSService.getInstance(this)
        ttsService.initialize()

        contactManager = ContactManager.getInstance(this)

        recyclerView = findViewById(R.id.recycler_video_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(false)

        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        stateLayout = findViewById(R.id.layout_state)
        stateTitle = findViewById(R.id.tv_state_title)
        stateMessage = findViewById(R.id.tv_state_message)
        stateActionButton = findViewById(R.id.btn_state_action)
        stateActionText = findViewById(R.id.tv_state_action)

        adapter = VideoCallContactAdapter(
            contactList,
            onContactClick = { contact -> startVideoCall(contact) }
        )

        manageAdapter = ContactManageAdapter(
            contactList,
            onDeleteClick = { contact -> deleteContact(contact) }
        )

        recyclerView.adapter = adapter

        findViewById<CardView>(R.id.btn_back).setOnClickListener {
            if (isManageMode) {
                switchToCallMode()
            } else {
                finish()
            }
        }

        modeActionButton.setOnClickListener {
            if (isManageMode) {
                showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }

        updateModeUi()
        loadContacts()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsService.stop()
        WeChatAccessibilityService.getInstance()?.clearStateCallback()
    }

    private fun switchToManageMode() {
        isManageMode = true
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = manageAdapter
        manageAdapter.notifyDataSetChanged()
        updateModeUi()
        updateState()
    }

    private fun switchToCallMode() {
        isManageMode = false
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
        updateModeUi()
        updateState()
    }

    private fun updateModeUi() {
        val text = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = text
        modeActionButton.contentDescription = text
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_contact_name)

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<CardView>(R.id.btn_confirm).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.input_contact_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            contactManager.addContact(
                Contact(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    wechatId = name
                )
            )
            loadContacts()
            Toast.makeText(this, getString(R.string.contact_added_named, name), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteContact(contact: Contact) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_contact_title)
            .setMessage(getString(R.string.video_contact_delete_message, contact.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                contactManager.removeContact(contact.id)
                loadContacts()
                Toast.makeText(this, getString(R.string.contact_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.show()
    }

    private fun showAccessibilityDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<CardView>(R.id.btn_open_settings).setOnClickListener {
            PermissionUtil.openAccessibilitySettings(this)
            dialog.dismiss()
        }

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showOverlayPermissionDialog(contact: Contact) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.action_go_to_settings) { _, _ ->
                PermissionUtil.openOverlaySettings(this)
            }
            .setNegativeButton(R.string.action_continue) { _, _ ->
                continueVideoCall(contact)
            }
            .setNeutralButton(R.string.action_cancel, null)
            .show()
    }

    private fun loadContacts() {
        contactList.clear()
        contactList.addAll(contactManager.getContacts())
        adapter.notifyDataSetChanged()
        manageAdapter.notifyDataSetChanged()
        updateState()
    }

    private fun updateState() {
        if (contactList.isNotEmpty()) {
            recyclerView.isVisible = true
            stateLayout.isVisible = false
            stateActionButton.setOnClickListener(null)
            return
        }

        recyclerView.isVisible = false
        stateLayout.isVisible = true
        stateTitle.text = getString(R.string.state_video_empty_title)
        stateMessage.text = getString(
            if (isManageMode) R.string.state_video_manage_empty_message else R.string.state_video_empty_message
        )
        stateActionText.text = getString(
            if (isManageMode) R.string.state_video_empty_action_add else R.string.state_video_empty_action_manage
        )
        stateActionButton.setOnClickListener {
            if (isManageMode) {
                showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }
    }

    private fun startVideoCall(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            ttsService.speak(getString(R.string.network_unavailable_detail))
            Toast.makeText(this, getString(R.string.network_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val serviceName = "${packageName}/${WeChatAccessibilityService::class.java.name}"
        if (!PermissionUtil.isAccessibilityServiceEnabled(this, serviceName)) {
            ttsService.speak(getString(R.string.accessibility_required))
            showAccessibilityDialog()
            return
        }

        if (!PermissionUtil.canDrawOverlays(this)) {
            showOverlayPermissionDialog(contact)
            return
        }

        continueVideoCall(contact)
    }

    private fun continueVideoCall(contact: Contact) {
        if (packageManager.getLaunchIntentForPackage("com.tencent.mm") == null) {
            ttsService.speak(getString(R.string.wechat_not_installed_detail))
            Toast.makeText(this, getString(R.string.wechat_not_installed), Toast.LENGTH_SHORT).show()
            return
        }

        ttsService.speak(getString(R.string.starting_video_call_detail))
        Toast.makeText(this, getString(R.string.starting_video_call), Toast.LENGTH_SHORT).show()

        val service = WeChatAccessibilityService.getInstance()
        if (service == null) {
            ttsService.speak(getString(R.string.accessibility_service_not_running))
            Toast.makeText(this, getString(R.string.accessibility_service_not_running), Toast.LENGTH_SHORT).show()
            return
        }

        service.setStateCallback { state, success ->
            runOnUiThread {
                ttsService.speak(state)
                Toast.makeText(this, state, Toast.LENGTH_SHORT).show()

                if (!success || state.contains("已发起")) {
                    service.clearStateCallback()
                    if (success) {
                        finish()
                    }
                }
            }
        }

        contactManager.incrementCallCount(contact.id)
        loadContacts()
        service.requestVideoCall(contact.name)
    }
}
