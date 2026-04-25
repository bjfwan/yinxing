package com.yinxing.launcher.data.contact

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

class ContactSqliteStore(
    context: Context,
    groupKey: String = GROUP_WECHAT
) {
    private val groupKey = normalizeGroupKey(groupKey)
    private val helper = ContactDatabaseHelper(context.applicationContext)

    companion object {
        const val DATABASE_NAME = "launcher_contacts.db"
        const val GROUP_WECHAT = "wechat"
        const val GROUP_PHONE = "phone"

        fun deleteDatabase(context: Context): Boolean {
            return context.applicationContext.deleteDatabase(DATABASE_NAME)
        }

        fun normalizeGroupKey(groupKey: String): String {
            return when (val normalized = groupKey.trim()) {
                GROUP_WECHAT, "wechat_contacts" -> GROUP_WECHAT
                GROUP_PHONE, "phone_contacts" -> GROUP_PHONE
                else -> normalized
            }
        }
    }

    fun getContacts(): List<Contact> {
        val db = database()
        val contacts = mutableListOf<Contact>()
        db.query(
            TABLE_CONTACTS,
            null,
            "$COL_GROUP_KEY = ?",
            arrayOf(groupKey),
            null,
            null,
            "$COL_IS_PINNED DESC, $COL_CALL_COUNT DESC, $COL_LAST_CALL_TIME DESC, $COL_NAME ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                contacts += cursor.toContact()
            }
        }
        return contacts
    }

    fun count(): Int {
        val db = database()
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_CONTACTS WHERE $COL_GROUP_KEY = ?",
            arrayOf(groupKey)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun upsert(contact: Contact) {
        upsertAll(listOf(contact))
    }

    fun upsertAll(contacts: Collection<Contact>) {
        if (contacts.isEmpty()) {
            return
        }
        val db = database()
        db.beginTransaction()
        try {
            contacts.forEach { contact ->
                db.insertWithOnConflict(
                    TABLE_CONTACTS,
                    null,
                    contact.toValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun update(contact: Contact): Boolean {
        val db = database()
        return db.update(
            TABLE_CONTACTS,
            contact.toValues(),
            "$COL_GROUP_KEY = ? AND $COL_ID = ?",
            arrayOf(groupKey, contact.id)
        ) > 0
    }

    fun remove(contactId: String): Boolean {
        val db = database()
        return db.delete(
            TABLE_CONTACTS,
            "$COL_GROUP_KEY = ? AND $COL_ID = ?",
            arrayOf(groupKey, contactId)
        ) > 0
    }

    fun incrementCallCount(contactId: String, lastCallTime: Long): Boolean {
        val db = database()
        val statement = db.compileStatement(
            "UPDATE $TABLE_CONTACTS SET $COL_CALL_COUNT = $COL_CALL_COUNT + 1, $COL_LAST_CALL_TIME = ? " +
                "WHERE $COL_GROUP_KEY = ? AND $COL_ID = ?"
        )
        return try {
            statement.bindLong(1, lastCallTime)
            statement.bindString(2, groupKey)
            statement.bindString(3, contactId)
            statement.executeUpdateDelete() > 0
        } finally {
            statement.close()
        }
    }

    fun setPinned(contactId: String, pinned: Boolean): Boolean {
        val db = database()
        val statement = db.compileStatement(
            "UPDATE $TABLE_CONTACTS SET $COL_IS_PINNED = ? WHERE $COL_GROUP_KEY = ? AND $COL_ID = ?"
        )
        return try {
            statement.bindLong(1, if (pinned) 1L else 0L)
            statement.bindString(2, groupKey)
            statement.bindString(3, contactId)
            statement.executeUpdateDelete() > 0
        } finally {
            statement.close()
        }
    }

    private fun database(): SQLiteDatabase {
        return helper.writableDatabase
    }

    fun close() {
        helper.close()
    }

    private fun Contact.toValues(): ContentValues {
        val normalized = normalized()
        return ContentValues().apply {
            put(COL_GROUP_KEY, groupKey)
            put(COL_ID, normalized.id)
            put(COL_NAME, normalized.name)
            put(COL_PHONE_NUMBER, normalized.phoneNumber)
            put(COL_WECHAT_ID, normalized.wechatId)
            put(COL_AVATAR_URI, normalized.avatarUri)
            put(COL_PREFERRED_ACTION, normalized.preferredAction.name)
            put(COL_IS_PINNED, if (normalized.isPinned) 1 else 0)
            put(COL_CALL_COUNT, normalized.callCount)
            put(COL_LAST_CALL_TIME, normalized.lastCallTime)
            put(COL_SEARCH_KEYWORDS, encodeKeywords(normalized.searchKeywords))
            put(COL_AUTO_ANSWER, if (normalized.autoAnswer) 1 else 0)
        }
    }

    private fun Cursor.toContact(): Contact {
        val phoneNumber = nullableString(COL_PHONE_NUMBER)
        val wechatId = nullableString(COL_WECHAT_ID)
        return Contact(
            id = string(COL_ID),
            name = string(COL_NAME),
            phoneNumber = phoneNumber,
            wechatId = wechatId,
            avatarUri = nullableString(COL_AVATAR_URI),
            preferredAction = Contact.PreferredAction.fromStorage(
                string(COL_PREFERRED_ACTION),
                phoneNumber,
                wechatId
            ),
            isPinned = int(COL_IS_PINNED) != 0,
            callCount = int(COL_CALL_COUNT),
            lastCallTime = long(COL_LAST_CALL_TIME),
            searchKeywords = decodeKeywords(string(COL_SEARCH_KEYWORDS)),
            autoAnswer = int(COL_AUTO_ANSWER) != 0
        ).normalized()
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

    private fun Cursor.nullableString(column: String): String? =
        getString(getColumnIndexOrThrow(column))?.trim()?.takeIf { it.isNotEmpty() }

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun encodeKeywords(keywords: List<String>): String {
        val array = JSONArray()
        keywords.forEach(array::put)
        return array.toString()
    }

    private fun decodeKeywords(value: String): List<String> {
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optString(index).trim()
                    if (item.isNotEmpty()) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

private class ContactDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, ContactSqliteStore.DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTACTS (
                $COL_GROUP_KEY TEXT NOT NULL,
                $COL_ID TEXT NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_PHONE_NUMBER TEXT,
                $COL_WECHAT_ID TEXT,
                $COL_AVATAR_URI TEXT,
                $COL_PREFERRED_ACTION TEXT NOT NULL,
                $COL_IS_PINNED INTEGER NOT NULL,
                $COL_CALL_COUNT INTEGER NOT NULL,
                $COL_LAST_CALL_TIME INTEGER NOT NULL,
                $COL_SEARCH_KEYWORDS TEXT NOT NULL,
                $COL_AUTO_ANSWER INTEGER NOT NULL,
                PRIMARY KEY ($COL_GROUP_KEY, $COL_ID)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX $INDEX_CONTACTS_GROUP_SORT ON $TABLE_CONTACTS " +
                "($COL_GROUP_KEY, $COL_IS_PINNED, $COL_CALL_COUNT, $COL_LAST_CALL_TIME, $COL_NAME)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

private const val DATABASE_VERSION = 1
private const val TABLE_CONTACTS = "contacts"
private const val INDEX_CONTACTS_GROUP_SORT = "index_contacts_group_sort"
private const val COL_GROUP_KEY = "group_key"
private const val COL_ID = "id"
private const val COL_NAME = "name"
private const val COL_PHONE_NUMBER = "phone_number"
private const val COL_WECHAT_ID = "wechat_id"
private const val COL_AVATAR_URI = "avatar_uri"
private const val COL_PREFERRED_ACTION = "preferred_action"
private const val COL_IS_PINNED = "is_pinned"
private const val COL_CALL_COUNT = "call_count"
private const val COL_LAST_CALL_TIME = "last_call_time"
private const val COL_SEARCH_KEYWORDS = "search_keywords"
private const val COL_AUTO_ANSWER = "auto_answer"
