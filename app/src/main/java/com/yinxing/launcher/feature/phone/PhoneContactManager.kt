package com.yinxing.launcher.feature.phone

import android.content.Context
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactSqliteStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneContactManager(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val store = ContactSqliteStore(
        context.applicationContext,
        groupKey = ContactSqliteStore.GROUP_PHONE
    )
    private val persistDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var sortedCache: List<Contact>? = null

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
        sortedCache?.let { return it }
        val result = store.getContacts()
        sortedCache = result
        return result
    }

    fun getContactCount(): Int {
        return sortedCache?.size ?: store.count()
    }

    suspend fun addContact(contact: Contact) {
        withContext(persistDispatcher) {
            store.upsert(contact)
        }
        invalidateCache()
    }

    suspend fun addContacts(contacts: Collection<Contact>) {
        if (contacts.isEmpty()) {
            return
        }
        withContext(persistDispatcher) {
            store.upsertAll(contacts)
        }
        invalidateCache()
    }

    suspend fun updateContact(contact: Contact) {
        val updated = withContext(persistDispatcher) {
            store.update(contact)
        }
        if (updated) {
            invalidateCache()
        }
    }

    suspend fun removeContact(contactId: String) {
        withContext(persistDispatcher) {
            store.remove(contactId)
        }
        invalidateCache()
    }

    suspend fun incrementCallCount(contactId: String): Boolean {
        val updated = withContext(persistDispatcher) {
            store.incrementCallCount(contactId, currentTimeMillis())
        }
        if (updated) {
            invalidateCache()
        }
        return updated
    }

    fun close() {
        store.close()
    }

    @Synchronized
    private fun invalidateCache() {
        sortedCache = null
    }
}
