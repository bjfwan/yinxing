package com.yinxing.launcher.feature.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object AppUpdateDownloader {

    sealed class Result {
        data class Success(val file: File) : Result()
        data class Failure(val message: String) : Result()
    }

    fun targetFile(context: Context, versionCode: Int): File =
        File(File(context.cacheDir, "updates"), "app-update-$versionCode.apk")

    private fun markerFile(target: File): File = File(target.parentFile, "${target.name}.ok")

    fun isDownloaded(context: Context, info: AppUpdateInfo): Boolean {
        val file = targetFile(context, info.versionCode)
        return file.isFile && markerFile(file).isFile && verifySha256(file, info.sha256)
    }

    suspend fun download(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val target = targetFile(context, info.versionCode)
        val marker = markerFile(target)
        target.parentFile?.mkdirs()

        if (marker.isFile && verifySha256(target, info.sha256)) {
            return@withContext Result.Success(target)
        }
        marker.delete()

        var lastError = "unknown"
        for (url in info.downloadUrls) {
            try {
                fetch(url, target, onProgress)
                if (verifySha256(target, info.sha256)) {
                    marker.writeText("ok")
                    return@withContext Result.Success(target)
                }
                lastError = "checksum"
                target.delete()
            } catch (e: Exception) {
                lastError = e.message ?: "network"
            }
        }
        Result.Failure(lastError)
    }

    fun clear(context: Context) {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    private fun fetch(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val offset = if (target.isFile) target.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 15_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw java.io.IOException("HTTP $status")
            val append = status == HttpURLConnection.HTTP_PARTIAL && offset > 0
            val total = connection.contentLengthLong.let { length ->
                if (length < 0) -1L else length + if (append) offset else 0L
            }
            val written = if (append) offset else 0L
            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = written
                    onProgress(downloaded, total)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (expected.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected.trim(), ignoreCase = true)
    }
}
