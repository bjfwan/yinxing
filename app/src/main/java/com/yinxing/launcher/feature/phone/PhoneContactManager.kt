package com.yinxing.launcher.feature.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactStorage

class PhoneContactManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("phone_contacts", Context.MODE_PRIVATE)

    private var cache: MutableList<Contact>? = null

    companion object {
        @Volatile
        private var instance: PhoneContactManager? = null

        fun getInstance(context: Context): PhoneContactManager {
            return instance ?: synchronized(this) {
                instance ?: PhoneContactManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getContacts(): List<Contact> {
        if (cache == null) {
            cache = ContactStorage.decode(prefs.getString("contacts", "[]"))
        }
        return cache.orEmpty().sortedWith(
            compareByDescending<Contact> { it.isPinned }
                .thenByDescending { it.callCount }
                .thenBy { it.name }
        )
    }

    fun addContact(contact: Contact) {
        ensureCache()
        cache?.removeAll { it.id == contact.id }
        cache?.add(contact.normalized())
        save()
    }

    fun updateContact(contact: Contact) {
        ensureCache()
        val index = cache?.indexOfFirst { it.id == contact.id } ?: -1
        if (index >= 0) {
            cache?.set(index, contact.normalized())
            save()
        }
    }

    fun removeContact(contactId: String) {
        ensureCache()
        cache?.removeAll { it.id == contactId }
        save()
    }

    fun incrementCallCount(contactId: String) {
        ensureCache()
        val index = cache?.indexOfFirst { it.id == contactId } ?: -1
        if (index >= 0) {
            val c = cache?.get(index) ?: return
            cache?.set(index, c.copy(callCount = c.callCount + 1, lastCallTime = System.currentTimeMillis()).normalized())
            save()
        }
    }

    private fun ensureCache() {
        if (cache == null) getContacts()
    }

    private fun save() {
        prefs.edit { putString("contacts", ContactStorage.encode(cache ?: emptyList())) }
    }
}
