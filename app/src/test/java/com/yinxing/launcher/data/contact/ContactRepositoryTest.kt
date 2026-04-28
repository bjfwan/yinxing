package com.yinxing.launcher.data.contact

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContactSqliteStore.deleteDatabase(context)
        context.getSharedPreferences("contact_repository_migration", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("contacts", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("phone_contacts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun phoneAndWechatGroupsStaySeparate() = runTest {
        val phoneRepository = ContactRepository.phone(context)
        val wechatRepository = ContactRepository.wechat(context)

        phoneRepository.addContact(Contact(id = "phone", name = "电话", phoneNumber = "13800138000"))
        wechatRepository.addContact(Contact(id = "wechat", name = "视频", wechatId = "wx"))

        assertEquals(listOf("phone"), phoneRepository.getContacts().map { it.id })
        assertEquals(listOf("wechat"), wechatRepository.getContacts().map { it.id })

        phoneRepository.close()
        wechatRepository.close()
    }

    @Test
    fun repositoryCachesAndInvalidatesAfterUpdates() = runTest {
        val repository = ContactRepository.phone(context) { 2000L }

        repository.addContacts(
            listOf(
                Contact(id = "1", name = "张三"),
                Contact(id = "2", name = "李四", isPinned = true)
            )
        )
        assertEquals(listOf("2", "1"), repository.getContacts().map { it.id })

        repository.incrementCallCount("1")
        repository.setPinned("1", true)

        val updated = repository.getContacts().first { it.id == "1" }
        assertEquals(1, updated.callCount)
        assertEquals(2000L, updated.lastCallTime)
        assertTrue(updated.isPinned)

        repository.close()
    }

    @Test
    fun wechatRepositoryMigratesLegacyContactsAndClearsOldPrefs() {
        seedLegacyContacts(
            prefsName = "contacts",
            key = "legacy_contacts",
            contacts = listOf(
                Contact(id = "wechat-1", name = " 老王 ", wechatId = " wx_old ")
            )
        )
        seedLegacyContacts(
            prefsName = "wechat_contacts",
            key = "wechat_backup",
            contacts = listOf(
                Contact(id = "wechat-2", name = "小李", phoneNumber = "138 0013 8000")
            )
        )

        val repository = ContactRepository.wechat(context)

        assertEquals(listOf("wechat-1", "wechat-2"), repository.getContacts().map { it.id }.sorted())
        assertTrue(context.getSharedPreferences("contacts", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE).all.isEmpty())

        repository.close()
    }

    @Test
    fun phoneRepositoryMigratesLegacyPhoneContactsOnly() {
        seedLegacyContacts(
            prefsName = "phone_contacts",
            key = "phone_legacy",
            contacts = listOf(
                Contact(id = "phone-1", name = " 电话张三 ", phoneNumber = " 13800138000 ")
            )
        )
        seedLegacyContacts(
            prefsName = "wechat_contacts",
            key = "wechat_legacy",
            contacts = listOf(
                Contact(id = "wechat-1", name = "视频李四", wechatId = "wx_demo")
            )
        )

        val repository = ContactRepository.phone(context)

        assertEquals(listOf("phone-1"), repository.getContacts().map { it.id })
        assertTrue(context.getSharedPreferences("phone_contacts", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE).all.isNotEmpty())

        repository.close()
    }

    private fun seedLegacyContacts(
        prefsName: String,
        key: String,
        contacts: List<Contact>
    ) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(key, ContactStorage.encode(contacts))
            .commit()
    }
}
