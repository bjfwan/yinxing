package com.yinxing.launcher.common.lobster

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Transactional state storage in no-backup app data; callers use it from the diagnostics IO path. */
internal object LobsterAtomicStorage {
    private const val DATABASE_NAME = "lobster-diagnostics.db"
    private const val TABLE = "diagnostic_state"
    private val lock = Any()

    fun read(context: Context, key: String, databaseName: String = DATABASE_NAME): String? = synchronized(lock) {
        val db = open(context, databaseName)
        try {
            db.query(TABLE, arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } finally {
            db.close()
        }
    }

    fun write(context: Context, key: String, content: String?, databaseName: String = DATABASE_NAME) = synchronized(lock) {
        val db = open(context, databaseName)
        try {
            db.beginTransaction()
            try {
                if (content == null) {
                    db.delete(TABLE, "key = ?", arrayOf(key))
                } else {
                    val values = ContentValues().apply {
                        put("key", key)
                        put("value", content)
                    }
                    db.replaceOrThrow(TABLE, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    private fun open(context: Context, databaseName: String): SQLiteDatabase {
        val file = File(context.applicationContext.noBackupFilesDir, databaseName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            return db
        } catch (failure: Exception) {
            db.close()
            throw failure
        }
    }
}
