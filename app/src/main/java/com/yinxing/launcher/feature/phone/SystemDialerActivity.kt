package com.yinxing.launcher.feature.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterUsageEvents

/**
 * ROLE_DIALER 所需的 ACTION_DIAL 入口。实际拨号统一交给 TelecomManager，确保紧急号码
 * 仍由系统预装电话应用正确接管。
 *
 * Source: https://developer.android.com/develop/connectivity/telecom/dialer-app#becoming-the-default-phone-app
 */
class SystemDialerActivity : AppCompatActivity() {
    private lateinit var numberInput: EditText
    private var pendingNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pendingNumber
        pendingNumber = null
        if (granted && !number.isNullOrBlank()) placeCall(number)
        else {
            LobsterClient.reportUsage(this, LobsterUsageEvents.CALL_PERMISSION_DENIED)
            Toast.makeText(this, R.string.system_dialer_permission_missing, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_dialer)

        numberInput = findViewById(R.id.et_system_dialer_number)
        numberInput.setText(intent?.data?.schemeSpecificPart.orEmpty())
        numberInput.setSelection(numberInput.text.length)

        findViewById<android.view.View>(R.id.btn_system_dialer_call).setOnClickListener {
            requestCall(numberInput.text?.toString().orEmpty())
        }
        findViewById<android.view.View>(R.id.btn_system_dialer_close).setOnClickListener { finish() }
    }

    private fun requestCall(rawNumber: String) {
        val number = rawNumber.trim()
        if (number.isEmpty()) {
            Toast.makeText(this, R.string.system_dialer_number_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        placeCall(number)
    }

    @SuppressLint("MissingPermission")
    private fun placeCall(number: String) {
        val manager = getSystemService(TelecomManager::class.java)
        val result = runCatching {
            requireNotNull(manager) { "TelecomManager unavailable" }
            manager.placeCall(Uri.fromParts("tel", number, null), Bundle())
        }
        if (result.isSuccess) {
            LobsterClient.reportUsage(this, LobsterUsageEvents.OUTGOING_CALL_STARTED)
            finish()
        } else {
            LobsterClient.reportUsage(this, LobsterUsageEvents.OUTGOING_CALL_FAILED)
            Toast.makeText(this, R.string.system_dialer_call_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
