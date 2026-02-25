package com.bajianfeng.launcher.manager

import android.content.Context
import android.content.SharedPreferences
import com.bajianfeng.launcher.model.Contact
import org.json.JSONArray
import org.json.JSONObject

class ContactManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE)
    
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return contacts.sortedWith(compareByDescending<Contact> { it.isPinned }.thenByDescending { it.callCount })
    }
    
    fun addContact(contact: Contact) {
        val contacts = getContacts().toMutableList()
        contacts.removeAll { it.id == contact.id }
        contacts.add(contact)
        saveContacts(contacts)
    }
    
    fun removeContact(contactId: String) {
        val contacts = getContacts().toMutableList()
        contacts.removeAll { it.id == contactId }
        saveContacts(contacts)
    }
    
    fun updateContact(contact: Contact) {
        val contacts = getContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            contacts[index] = contact
            saveContacts(contacts)
        }
    }
    
    fun incrementCallCount(contactId: String) {
        val contacts = getContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contactId }
        if (index >= 0) {
            val contact = contacts[index]
            contacts[index] = contact.copy(
                callCount = contact.callCount + 1,
                lastCallTime = System.currentTimeMillis()
            )
            saveContacts(contacts)
        }
    }
    
    private fun saveContacts(contacts: List<Contact>) {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("wechatId", contact.wechatId ?: "")
                put("avatarUri", contact.avatarUri ?: "")
                put("isPinned", contact.isPinned)
                put("callCount", contact.callCount)
                put("lastCallTime", contact.lastCallTime)
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString("contacts", jsonArray.toString()).apply()
    }
}
