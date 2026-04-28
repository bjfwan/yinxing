package com.yinxing.launcher.feature.phone

import android.content.Context
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactRepository

class PhoneContactManager(
    context: Context,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val repository: ContactRepository = ContactRepository.phone(context, currentTimeMillis)
) {
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
        return repository.getContacts()
    }

    fun getContactCount(): Int {
        return repository.getContactCount()
    }

    suspend fun addContact(contact: Contact) {
        repository.addContact(contact)
    }

    suspend fun addContacts(contacts: Collection<Contact>) {
        repository.addContacts(contacts)
    }

    suspend fun updateContact(contact: Contact) {
        repository.updateContact(contact)
    }

    suspend fun removeContact(contactId: String) {
        repository.removeContact(contactId)
    }

    suspend fun incrementCallCount(contactId: String): Boolean {
        return repository.incrementCallCount(contactId)
    }

    fun close() {
        repository.close()
    }
}
