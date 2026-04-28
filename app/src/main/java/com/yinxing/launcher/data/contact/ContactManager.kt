package com.yinxing.launcher.data.contact

import android.content.Context

class ContactManager(
    context: Context,
    repository: ContactRepository? = null,
    currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val repository: ContactRepository = repository ?: ContactRepository.wechat(context, currentTimeMillis)

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
        return repository.getContacts()
    }

    fun getContactCount(): Int {
        return repository.getContactCount()
    }

    suspend fun addContact(contact: Contact) {
        repository.addContact(contact)
    }

    suspend fun removeContact(contactId: String) {
        repository.removeContact(contactId)
    }

    suspend fun updateContact(contact: Contact) {
        repository.updateContact(contact)
    }

    suspend fun incrementCallCount(contactId: String): Boolean {
        return repository.incrementCallCount(contactId)
    }

    suspend fun setPinned(contactId: String, pinned: Boolean) {
        repository.setPinned(contactId, pinned)
    }

    fun close() {
        repository.close()
    }
}
