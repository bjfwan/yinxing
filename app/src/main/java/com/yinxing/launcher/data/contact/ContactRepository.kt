package com.yinxing.launcher.data.contact

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(
    context: Context,
    groupKey: String,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val normalizedGroupKey = ContactSqliteStore.normalizeGroupKey(groupKey)
    private val store = ContactSqliteStore(
        appContext,
        groupKey = normalizedGroupKey
    )
    private val migrationPrefs = appContext.getSharedPreferences(PREFS_CONTACT_MIGRATION, Context.MODE_PRIVATE)
    private val persistDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var sortedCache: List<Contact>? = null

    init {
        migrateLegacyContacts()
    }

    companion object {
        private const val PREFS_CONTACT_MIGRATION = "contact_repository_migration"
        private const val PREFS_LEGACY_CONTACTS = "contacts"
        private const val PREFS_LEGACY_WECHAT = "wechat_contacts"
        private const val PREFS_LEGACY_PHONE = "phone_contacts"

        fun phone(
            context: Context,
            currentTimeMillis: () -> Long = System::currentTimeMillis
        ): ContactRepository {
            return ContactRepository(
                context = context,
                groupKey = ContactSqliteStore.GROUP_PHONE,
                currentTimeMillis = currentTimeMillis
            )
        }

        fun wechat(
            context: Context,
            currentTimeMillis: () -> Long = System::currentTimeMillis
        ): ContactRepository {
            return ContactRepository(
                context = context,
                groupKey = ContactSqliteStore.GROUP_WECHAT,
                currentTimeMillis = currentTimeMillis
            )
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
        addContacts(listOf(contact))
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

    private fun migrateLegacyContacts() {
        legacyPreferenceNames().forEach(::migrateLegacyPreference)
    }

    private fun legacyPreferenceNames(): List<String> {
        return when (normalizedGroupKey) {
            ContactSqliteStore.GROUP_WECHAT -> listOf(PREFS_LEGACY_CONTACTS, PREFS_LEGACY_WECHAT)
            ContactSqliteStore.GROUP_PHONE -> listOf(PREFS_LEGACY_PHONE)
            else -> emptyList()
        }
    }

    private fun migrateLegacyPreference(prefName: String) {
        if (migrationPrefs.getBoolean(prefName, false)) {
            return
        }

        val legacyPrefs = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val legacyEntries = legacyPrefs.all
        if (legacyEntries.isEmpty()) {
            markLegacyMigrated(prefName)
            return
        }

        val contacts = legacyEntries.values
            .asSequence()
            .mapNotNull { it as? String }
            .flatMap { ContactStorage.decode(it).asSequence() }
            .toList()

        if (contacts.isNotEmpty()) {
            store.upsertAll(contacts)
            invalidateCache()
        }

        legacyPrefs.edit(commit = true) {
            clear()
        }
        markLegacyMigrated(prefName)
    }

    private fun markLegacyMigrated(prefName: String) {
        migrationPrefs.edit {
            putBoolean(prefName, true)
        }
    }
}
