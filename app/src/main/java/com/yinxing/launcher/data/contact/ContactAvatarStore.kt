package com.yinxing.launcher.data.contact

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactAvatarStore {
    private const val DIRECTORY_NAME = "contact-avatars"

    suspend fun saveFromUri(context: Context, sourceUri: Uri, contactId: String): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = MediaThumbnailLoader.loadUriThumbnailBlocking(context, sourceUri, 640, 640)
                    ?: return@withContext null
                val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
                val targetFile = File(directory, "$contactId.jpg")
                FileOutputStream(targetFile).use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
                }
                val targetUri = Uri.fromFile(targetFile)
                MediaThumbnailLoader.evictUri(targetUri)
                targetUri.toString()

            }.getOrNull()
        }
    }

    suspend fun deleteOwnedAvatar(context: Context, avatarUri: String?) {
        withContext(Dispatchers.IO) {
            val uri = avatarUri?.let(Uri::parse) ?: return@withContext
            if (uri.scheme != ContentResolver.SCHEME_FILE) {
                return@withContext
            }
            val directory = File(context.filesDir, DIRECTORY_NAME)
            val targetFile = uri.path?.let(::File) ?: return@withContext
            if (targetFile.parentFile?.absolutePath == directory.absolutePath && targetFile.exists()) {
                targetFile.delete()
                MediaThumbnailLoader.evictUri(uri)

            }
        }
    }
}
