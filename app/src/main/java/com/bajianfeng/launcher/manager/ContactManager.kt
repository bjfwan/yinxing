package com.bajianfeng.launcher.manager

import android.content.Context
import android.content.SharedPreferences
import com.bajianfeng.launcher.model.Contact
import org.json.JSONArray
import org.json.JSONObject

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
            return it.sortedWith(compareByDescending<Contact> { c -> c.isPinned }.thenByDescending { c -> c.callCount })
        }

        val json = prefs.getString("contacts", "[]") ?: "[]"
        val contacts = mutableListOf<Contact>()

        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                contacts.add(
                    Contact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        wechatId = obj.optString("wechatId", null),
                        avatarUri = obj.optString("avatarUri", null),
                        isPinned = obj.optBoolean("isPinned", false),
                        callCount = obj.optInt("callCount", 0),
                        lastCallTime = obj.optLong("lastCallTime", 0)
                    )
                )
            }
        } catch (_: Exception) {
        }

        cachedContacts = contacts
        return contacts.sortedWith(compareByDescending<Contact> { it.isPinned }.thenByDescending { it.callCount })
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
            val contact = cachedContacts!![index]
            cachedContacts!![index] = contact.copy(
                callCount = contact.callCount + 1,
                lastCallTime = System.currentTimeMillis()
            )
            saveContacts()
        }
    }

    private fun ensureCache() {
        if (cachedContacts == null) getContacts()
    }

    private fun saveContacts() {
        val contacts = cachedContacts ?: return
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            jsonArray.put(JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("wechatId", contact.wechatId ?: "")
                put("avatarUri", contact.avatarUri ?: "")
                put("isPinned", contact.isPinned)
                put("callCount", contact.callCount)
                put("lastCallTime", contact.lastCallTime)
            })
        }
        prefs.edit().putString("contacts", jsonArray.toString()).commit()
    }
}
