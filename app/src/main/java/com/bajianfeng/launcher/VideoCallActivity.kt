package com.bajianfeng.launcher

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.manager.ContactManager
import com.bajianfeng.launcher.model.Contact
import com.bajianfeng.launcher.service.TTSService
import com.bajianfeng.launcher.service.WeChatAccessibilityService
import com.bajianfeng.launcher.util.NetworkUtil
import com.bajianfeng.launcher.util.PermissionUtil
import java.util.UUID

class VideoCallActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoCallContactAdapter
    private lateinit var manageAdapter: ContactManageAdapter
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

        findViewById<CardView>(R.id.btn_add_contact)?.setOnClickListener {
            if (isManageMode) {
                showAddContactDialog()
            } else {
                switchToManageMode()
            }
        }

        loadContacts()
        checkAccessibilityService()
    }

    override fun onDestroy() {
        super.onDestroy()
        WeChatAccessibilityService.getInstance()?.setStateCallback(null)
    }

    private fun switchToManageMode() {
        isManageMode = true
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = manageAdapter
        manageAdapter.notifyDataSetChanged()
    }

    private fun switchToCallMode() {
        isManageMode = false
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
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
                Toast.makeText(this, "请输入联系人姓名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val contact = Contact(
                id = UUID.randomUUID().toString(),
                name = name,
                wechatId = name
            )

            contactManager.addContact(contact)
            contactList.add(0, contact)
            manageAdapter.notifyItemInserted(0)
            adapter.notifyDataSetChanged()

            Toast.makeText(this, "已添加联系人：$name", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteContact(contact: Contact) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("删除联系人")
            .setMessage("确定要删除 ${contact.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                contactManager.removeContact(contact.id)
                val position = contactList.indexOf(contact)
                if (position >= 0) {
                    contactList.removeAt(position)
                    manageAdapter.notifyItemRemoved(position)
                    adapter.notifyDataSetChanged()
                }
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    private fun checkAccessibilityService() {
        val serviceName = "${packageName}/${WeChatAccessibilityService::class.java.name}"
        if (!PermissionUtil.isAccessibilityServiceEnabled(this, serviceName)) {
            showAccessibilityDialog()
            return
        }

        if (!PermissionUtil.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        }
    }

    private fun showAccessibilityDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<CardView>(R.id.btn_open_settings).setOnClickListener {
            PermissionUtil.openAccessibilitySettings(this)
            dialog.dismiss()
        }

        dialogView.findViewById<CardView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

    private fun showOverlayPermissionDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("为了显示操作状态，需要开启悬浮窗权限")
            .setPositiveButton("去设置") { _, _ ->
                PermissionUtil.openOverlaySettings(this)
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    private fun loadContacts() {
        contactList.clear()
        contactList.addAll(contactManager.getContacts())
        adapter.notifyDataSetChanged()
    }

    private fun startVideoCall(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            ttsService.speak("网络未连接，请检查网络")
            Toast.makeText(this, "网络未连接", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceName = "${packageName}/${WeChatAccessibilityService::class.java.name}"
        if (!PermissionUtil.isAccessibilityServiceEnabled(this, serviceName)) {
            ttsService.speak("请先开启无障碍权限")
            showAccessibilityDialog()
            return
        }

        try {
            packageManager.getPackageInfo("com.tencent.mm", 0)
        } catch (_: Exception) {
            ttsService.speak("未安装微信应用")
            Toast.makeText(this, "未安装微信", Toast.LENGTH_SHORT).show()
            return
        }

        contactManager.incrementCallCount(contact.id)

        ttsService.speak("正在为您拨打视频电话")
        Toast.makeText(this, "正在发起视频通话...", Toast.LENGTH_SHORT).show()

        val service = WeChatAccessibilityService.getInstance()
        if (service == null) {
            ttsService.speak("无障碍服务未运行")
            Toast.makeText(this, "无障碍服务未运行，请重新开启", Toast.LENGTH_SHORT).show()
            return
        }

        service.setStateCallback { state, success ->
            runOnUiThread {
                ttsService.speak(state)
                Toast.makeText(this, state, Toast.LENGTH_SHORT).show()

                if (state.contains("已发起") || state.contains("失败")) {
                    service.setStateCallback(null)
                    if (success) {
                        finish()
                    }
                }
            }
        }

        val intent = android.content.Intent(this, WeChatAccessibilityService::class.java)
        intent.action = WeChatAccessibilityService.ACTION_START_VIDEO_CALL
        intent.putExtra(WeChatAccessibilityService.EXTRA_CONTACT_NAME, contact.name)
        startService(intent)
    }
}
