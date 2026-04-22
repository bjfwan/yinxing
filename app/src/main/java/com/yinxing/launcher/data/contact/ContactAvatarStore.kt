package com.bajianfeng.launcher.data.contact

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import java.io.File
import java.io.FileOutputStream

object ContactAvatarStore {
    private const val DIRECTORY_NAME = "contact-avatars"

    fun saveFromUri(context: Context, sourceUri: Uri, contactId: String): String? {
        return runCatching {
            val bitmap = MediaThumbnailLoader.loadUriThumbnailBlocking(context, sourceUri, 640, 640)
                ?: return null
            val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
            val targetFile = File(directory, "$contactId.jpg")
            FileOutputStream(targetFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
            }
            MediaThumbnailLoader.clearIconCache()
            Uri.fromFile(targetFile).toString()
        }.getOrNull()
    }

    fun deleteOwnedAvatar(context: Context, avatarUri: String?) {
        val uri = avatarUri?.let(Uri::parse) ?: return
        if (uri.scheme != ContentResolver.SCHEME_FILE) {
            return
        }
        val directory = File(context.filesDir, DIRECTORY_NAME)
        val targetFile = uri.path?.let(::File) ?: return
        if (targetFile.parentFile?.absolutePath == directory.absolutePath && targetFile.exists()) {
            targetFile.delete()
            MediaThumbnailLoader.clearIconCache()
        }
    }
}
