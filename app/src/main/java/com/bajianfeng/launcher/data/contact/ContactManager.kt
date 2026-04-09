package com.bajianfeng.launcher.data.contact

import android.content.Context
import android.content.SharedPreferences

class ContactManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE)
    private var cachedContacts: MutableList<Contact>? = null

    companion object {
        @Volatile
        private var instance: ContactManager? = null

        fun getInstance(context: Context): ContactManager {
            return instance ?: synchronized(this) {
                instance ?: ContactManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getContacts(): List<Contact> {
        cachedContacts?.let {
            return ContactStorage.sort(it)
        }

        cachedContacts = ContactStorage.decode(prefs.getString("contacts", "[]"))
        return ContactStorage.sort(cachedContacts.orEmpty())
    }

    fun addContact(contact: Contact) {
        ensureCache()
        cachedContacts?.removeAll { it.id == contact.id }
        cachedContacts?.add(contact)
        saveContacts()
    }

    fun removeContact(contactId: String) {
        ensureCache()
        cachedContacts?.removeAll { it.id == contactId }
        saveContacts()
    }

    fun updateContact(contact: Contact) {
        ensureCache()
        val index = cachedContacts?.indexOfFirst { it.id == contact.id } ?: -1
        if (index >= 0) {
            cachedContacts?.set(index, contact)
            saveContacts()
        }
    }

    fun incrementCallCount(contactId: String) {
        ensureCache()
        val index = cachedContacts?.indexOfFirst { it.id == contactId } ?: -1
        if (index >= 0) {
            val contact = cachedContacts?.get(index) ?: return
            cachedContacts?.set(
                index,
                contact.copy(
                callCount = contact.callCount + 1,
                lastCallTime = System.currentTimeMillis()
            )
            )
            saveContacts()
        }
    }

    private fun ensureCache() {
        if (cachedContacts == null) getContacts()
    }

    private fun saveContacts() {
        val contacts = cachedContacts ?: return
        prefs.edit().putString("contacts", ContactStorage.encode(contacts)).commit()
    }
}
