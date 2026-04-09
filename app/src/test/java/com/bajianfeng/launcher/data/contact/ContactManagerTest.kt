package com.bajianfeng.launcher.data.contact

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun addContactPersistsNormalizedAndSortedContacts() {
        val manager = ContactManager(context)

        manager.addContact(Contact(id = "1", name = " 张三 ", phoneNumber = "138 0013 8000"))
        manager.addContact(Contact(id = "2", name = "李四", isPinned = true))

        val contacts = ContactManager(context).getContacts()

        assertEquals(listOf("2", "1"), contacts.map { it.id })
        assertEquals("张三", contacts.last().name)
        assertTrue(contacts.last().searchKeywords.contains("13800138000"))
    }

    @Test
    fun incrementCallCountUsesInjectedClock() {
        val manager = ContactManager(context) { 123456L }
        manager.addContact(Contact(id = "1", name = "张三"))

        manager.incrementCallCount("1")

        val contact = ContactManager(context).getContacts().first()
        assertEquals(1, contact.callCount)
        assertEquals(123456L, contact.lastCallTime)
    }
}
