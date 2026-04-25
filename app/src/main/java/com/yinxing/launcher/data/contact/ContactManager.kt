package com.yinxing.launcher.data.contact

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactManager(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val store = ContactSqliteStore(
        context.applicationContext,
        groupKey = ContactSqliteStore.GROUP_WECHAT
    )
    private val persistDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var sortedCache: List<Contact>? = null

    companion object {
        @Volatile
        private var instance: ContactManager? = null

        fun getInstance(context: Context): ContactManager {
            return instance ?: synchronized(this) {
                instance ?: ContactManager(context.applicationContext).also { instance = it }
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

    suspend fun removeContact(contactId: String) {
        withContext(persistDispatcher) {
            store.remove(contactId)
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

    suspend fun incrementCallCount(contactId: String): Boolean {
        val updated = withContext(persistDispatcher) {
            store.incrementCallCount(contactId, currentTimeMillis())
        }
        if (updated) {
            invalidateCache()
        }
        return updated
    }

    suspend fun setPinned(contactId: String, pinned: Boolean) {
        val updated = withContext(persistDispatcher) {
            store.setPinned(contactId, pinned)
        }
        if (updated) {
            invalidateCache()
        }
    }

    fun close() {
        store.close()
    }

    @Synchronized
    private fun invalidateCache() {
        sortedCache = null
    }
}
