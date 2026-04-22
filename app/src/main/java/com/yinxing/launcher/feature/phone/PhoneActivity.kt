package com.bajianfeng.launcher.feature.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R

class PhoneActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val opened = contactIntents().any { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrElse { error ->
                if (error !is ActivityNotFoundException && error !is SecurityException) {
                    throw error
                }
                false
            }
        }

        if (!opened) {
            Toast.makeText(this, getString(R.string.open_contacts_failed), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun contactIntents(): List<Intent> {
        return listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS),
            Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI),
            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        )
    }
}
