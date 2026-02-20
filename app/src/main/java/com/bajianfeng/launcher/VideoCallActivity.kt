package com.bajianfeng.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.model.Contact
import com.bajianfeng.launcher.service.TTSService
import com.bajianfeng.launcher.service.WeChatAccessibilityService
import com.bajianfeng.launcher.util.NetworkUtil
import com.bajianfeng.launcher.util.PermissionUtil
import java.io.InputStream

class VideoCallActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoCallContactAdapter
    private val contactList = mutableListOf<Contact>()
    private lateinit var ttsService: TTSService

    companion object {
        private const val PERMISSION_REQUEST = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        ttsService = TTSService.getInstance(this)
        ttsService.initialize()

        recyclerView = findViewById(R.id.recycler_video_contacts)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        adapter = VideoCallContactAdapter(
            contactList,
            onContactClick = { contact -> startVideoCall(contact) }
        )
        recyclerView.adapter = adapter

        findViewById<CardView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        if (checkPermissions()) {
            loadContacts()
        } else {
            requestPermissions()
        }

        checkAccessibilityService()
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadContacts()
            } else {
                Toast.makeText(this, "需要联系人权限", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun checkAccessibilityService() {
        val serviceName = "${packageName}/${WeChatAccessibilityService::class.java.name}"
        if (!PermissionUtil.isAccessibilityServiceEnabled(this, serviceName)) {
            showAccessibilityDialog()
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

    private fun loadContacts() {
        contactList.clear()

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)
                val photoUri = it.getString(photoIndex)

                val avatarUri = photoUri

                contactList.add(
                    Contact(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        avatarUri = avatarUri
                    )
                )
            }
        }

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
            Toast.makeText(this, "请先开启无障碍权限", Toast.LENGTH_SHORT).show()
            showAccessibilityDialog()
            return
        }

        val pm = packageManager
        try {
            pm.getPackageInfo("com.tencent.mm", 0)
        } catch (e: Exception) {
            ttsService.speak("未安装微信应用")
            Toast.makeText(this, "未安装微信", Toast.LENGTH_SHORT).show()
            return
        }

        ttsService.speak("正在为您拨打视频电话")
        Toast.makeText(this, "正在发起视频通话...", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, WeChatAccessibilityService::class.java)
        intent.action = WeChatAccessibilityService.ACTION_START_VIDEO_CALL
        intent.putExtra(WeChatAccessibilityService.EXTRA_CONTACT_NAME, contact.name)
        startService(intent)

        WeChatAccessibilityService.getInstance()?.setStateCallback { state, success ->
            runOnUiThread {
                ttsService.speak(state)
                Toast.makeText(this, state, Toast.LENGTH_SHORT).show()
                
                if (state == "操作成功" || state.contains("失败")) {
                    if (success) {
                        finish()
                    }
                }
            }
        }
    }
}
