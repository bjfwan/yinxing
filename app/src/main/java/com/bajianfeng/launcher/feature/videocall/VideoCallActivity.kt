package com.bajianfeng.launcher.feature.videocall

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.ui.PageStateView
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager

class VideoCallActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var contactAdapter: VideoCallContactAdapter
    private lateinit var manageAdapter: ContactManageAdapter
    private lateinit var modeActionButton: CardView
    private lateinit var modeActionText: TextView
    private lateinit var pageStateView: PageStateView
    private lateinit var contactManager: ContactManager
    private lateinit var ttsService: TTSService
    private lateinit var videoCallCoordinator: VideoCallCoordinator
    private lateinit var dialogController: VideoContactDialogController
    private var isManageMode = false
    private var contacts: List<Contact> = emptyList()
    private var currentContactId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        contactManager = ContactManager.getInstance(this)
        ttsService = TTSService(this)
        ttsService.initialize()
        dialogController = VideoContactDialogController(this, contactManager, ::loadContacts)
        videoCallCoordinator = VideoCallCoordinator(
            context = this,
            ttsService = ttsService,
            onMessage = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            },
            onNeedAccessibilityPermission = ::showAccessibilityDialog,
            onNeedOverlayPermission = ::showOverlayPermissionDialog,
            onCallStarted = {
                currentContactId?.let(contactManager::incrementCallCount)
                runOnUiThread {
                    loadContacts()
                    finish()
                }
            }
        )

        recyclerView = findViewById(R.id.recycler_video_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(false)

        modeActionButton = findViewById(R.id.btn_mode_action)
        modeActionText = findViewById(R.id.tv_mode_action)
        pageStateView = findViewById(R.id.view_page_state)

        contactAdapter = VideoCallContactAdapter { contact ->
            currentContactId = contact.id
            videoCallCoordinator.start(contact)
        }
        manageAdapter = ContactManageAdapter { contact ->
            dialogController.showDeleteDialog(contact)
        }
        recyclerView.adapter = contactAdapter

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

    override fun onDestroy() {
        videoCallCoordinator.clearServiceCallback()
        ttsService.stop()
        ttsService.shutdown()
        super.onDestroy()
    }

    private fun switchToManageMode() {
        isManageMode = true
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = manageAdapter
        updateModeUi()
        renderState()
    }

    private fun switchToCallMode() {
        isManageMode = false
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = contactAdapter
        updateModeUi()
        renderState()
    }

    private fun updateModeUi() {
        val text = getString(if (isManageMode) R.string.action_add else R.string.action_manage)
        modeActionText.text = text
        modeActionButton.contentDescription = text
    }

    private fun loadContacts() {
        contacts = contactManager.getContacts()
        contactAdapter.submitList(contacts)
        manageAdapter.submitList(contacts)
        renderState()
    }

    private fun renderState() {
        if (contacts.isNotEmpty()) {
            recyclerView.isVisible = true
            pageStateView.hide()
            return
        }

        recyclerView.isVisible = false
        pageStateView.show(
            getString(R.string.state_video_empty_title),
            getString(
                if (isManageMode) R.string.state_video_manage_empty_message
                else R.string.state_video_empty_message
            ),
            getString(
                if (isManageMode) R.string.state_video_empty_action_add
                else R.string.state_video_empty_action_manage
            )
        ) {
            if (isManageMode) {
                dialogController.showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }
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
                videoCallCoordinator.continueWithoutOverlayCheck(contact)
            }
            .setNeutralButton(R.string.action_cancel, null)
            .show()
    }
}
