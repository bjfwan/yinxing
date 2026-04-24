package com.yinxing.launcher.feature.phone

import android.content.Context
import android.content.SharedPreferences
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneContactManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("phone_contacts", Context.MODE_PRIVATE)
    private val persistDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var cache: MutableList<Contact>? = null
    private var sortedCache: List<Contact>? = null
    private var sortedDirty = true

    companion object {
        @Volatile
        private var instance: PhoneContactManager? = null

        fun getInstance(context: Context): PhoneContactManager {
            return instance ?: synchronized(this) {
                instance ?: PhoneContactManager(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getContacts(): List<Contact> {
        sortedCache?.takeIf { !sortedDirty }?.let { return it }
        ensureCache()
        val result = ContactStorage.sort(cache.orEmpty())
        sortedCache = result
        sortedDirty = false
        return result
    }

    suspend fun addContact(contact: Contact) {
        persistSnapshot(updateSnapshot { contacts ->
            contacts.removeAll { it.id == contact.id }
            contacts.add(contact.normalized())
        })
    }

    suspend fun addContacts(contacts: Collection<Contact>) {
        if (contacts.isEmpty()) {
            return
        }
        val normalized = contacts.map(Contact::normalized)
        persistSnapshot(updateSnapshot { cached ->
            val ids = normalized.mapTo(hashSetOf()) { it.id }
            cached.removeAll { it.id in ids }
            cached.addAll(normalized)
        })
    }

    suspend fun updateContact(contact: Contact) {
        val snapshot = synchronized(this) {
            ensureCache()
            val contacts = cache ?: mutableListOf<Contact>().also { cache = it }
            val index = contacts.indexOfFirst { it.id == contact.id }
            if (index < 0) {
                return
            }
            contacts[index] = contact.normalized()
            markDirtyAndSnapshot()
        }
        persistSnapshot(snapshot)
    }

    suspend fun removeContact(contactId: String) {
        persistSnapshot(updateSnapshot { contacts ->
            contacts.removeAll { it.id == contactId }
        })
    }

    suspend fun incrementCallCount(contactId: String) {
        val snapshot = synchronized(this) {
            ensureCache()
            val contacts = cache ?: mutableListOf<Contact>().also { cache = it }
            val index = contacts.indexOfFirst { it.id == contactId }
            if (index < 0) {
                return
            }
            val contact = contacts[index]
            contacts[index] = contact.copy(
                callCount = contact.callCount + 1,
                lastCallTime = System.currentTimeMillis()
            ).normalized()
            markDirtyAndSnapshot()
        }
        persistSnapshot(snapshot)
    }

    @Synchronized
    private fun ensureCache() {
        if (cache == null) {
            cache = ContactStorage.decode(prefs.getString("contacts", "[]"))
        }
    }

    @Synchronized
    private fun updateSnapshot(block: (MutableList<Contact>) -> Unit): List<Contact> {
        ensureCache()
        val contacts = cache ?: mutableListOf<Contact>().also { cache = it }
        block(contacts)
        return markDirtyAndSnapshot()
    }

    @Synchronized
    private fun markDirtyAndSnapshot(): List<Contact> {
        sortedDirty = true
        sortedCache = null
        return cache?.toList().orEmpty()
    }

    private suspend fun persistSnapshot(snapshot: List<Contact>) {
        withContext(persistDispatcher) {
            val success = prefs.edit()
                .putString("contacts", ContactStorage.encode(snapshot))
                .commit()
            check(success) { "保存电话联系人失败" }
        }
    }
}
