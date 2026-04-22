package com.yinxing.launcher.data.contact

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 联系人管理器（升级版）。
 *
 * 优化：
 * - 增加排序结果缓存（sortedContactsCache）和脏标志（sortedDirty）
 * - 任何写操作将 sortedDirty 置 true，下次 getContacts() 时重新排序
 * - 数据未变化时直接返回缓存的排序结果，避免每次 getContacts() 都全量比较排序
 */
class ContactManager(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wechat_contacts", Context.MODE_PRIVATE)

    /** 原始联系人列表缓存（未排序） */
    private var cachedContacts: MutableList<Contact>? = null

    /** 排序结果缓存，避免每次 getContacts() 重新排序 */
    private var sortedContactsCache: List<Contact>? = null

    /** 排序结果是否需要重新计算 */
    private var sortedDirty = true

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
        // 优先返回缓存的排序结果
        val sorted = sortedContactsCache
        if (sorted != null && !sortedDirty) {
            return sorted
        }

        // 原始列表缓存未命中 → 从 SP 加载
        if (cachedContacts == null) {
            cachedContacts = ContactStorage.decode(prefs.getString("contacts", "[]"))
        }

        // 重新排序并缓存
        val result = ContactStorage.sort(cachedContacts.orEmpty())
        sortedContactsCache = result
        sortedDirty = false
        return result
    }

    fun addContact(contact: Contact) {
        ensureCache()
        cachedContacts?.removeAll { it.id == contact.id }
        cachedContacts?.add(contact.normalized())
        markDirtyAndSave()
    }

    fun removeContact(contactId: String) {
        ensureCache()
        cachedContacts?.removeAll { it.id == contactId }
        markDirtyAndSave()
    }

    fun updateContact(contact: Contact) {
        ensureCache()
        val index = cachedContacts?.indexOfFirst { it.id == contact.id } ?: -1
        if (index >= 0) {
            cachedContacts?.set(index, contact.normalized())
            markDirtyAndSave()
        }
    }

    fun incrementCallCount(contactId: String) {
        ensureCache()
        val index = cachedContacts?.indexOfFirst { it.id == contactId } ?: -1
        if (index >= 0) {
            val contact = cachedContacts?.get(index) ?: return
            cachedContacts?.set(
                index,
                contact.copy(
                    callCount = contact.callCount + 1,
                    lastCallTime = currentTimeMillis()
                ).normalized()
            )
            markDirtyAndSave()
        }
    }

    fun setPinned(contactId: String, pinned: Boolean) {
        ensureCache()
        val index = cachedContacts?.indexOfFirst { it.id == contactId } ?: -1
        if (index >= 0) {
            val contact = cachedContacts?.get(index) ?: return
            cachedContacts?.set(index, contact.copy(isPinned = pinned).normalized())
            markDirtyAndSave()
        }
    }

    private fun ensureCache() {
        if (cachedContacts == null) getContacts()
    }

    /** 标记排序结果脏并持久化 */
    private fun markDirtyAndSave() {
        sortedDirty = true
        saveContacts()
    }

    private fun saveContacts() {
        val contacts = cachedContacts ?: return
        prefs.edit {
            putString("contacts", ContactStorage.encode(contacts))
        }
    }
}
