package com.yinxing.launcher.common.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object MediaThumbnailLoader {
    private const val TAG = "MediaThumbnailLoader"
    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failedUriLoads = ConcurrentHashMap<String, Long>()
    private const val FAILED_URI_TTL_MS = 60 * 1000L

    suspend fun loadAppIcon(context: Context, packageName: String, sizePx: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            val cacheKey = "app:$packageName:$sizePx"
            bitmapCache.get(cacheKey)?.let { return@withContext it }

            val bitmap = runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable, sizePx, sizePx)
            }.getOrNull()

            bitmap?.let { bitmapCache.put(cacheKey, it) }
            bitmap
        }
    }

    suspend fun loadUriThumbnail(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            loadUriThumbnailBlocking(context, uri, reqWidth, reqHeight)
        }
    }

    suspend fun loadBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return loadUriThumbnail(context, uri, reqWidth, reqHeight)
    }

    fun loadUriThumbnailBlocking(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val cacheKey = "uri:$uri:$reqWidth:$reqHeight"
        failedUriLoads[cacheKey]?.let { failedAt ->
            if (System.currentTimeMillis() - failedAt < FAILED_URI_TTL_MS) {
                return null
            }
        }
        bitmapCache.get(cacheKey)?.let { return it }

        val bitmap = try {
            decodeSampledBitmap(
                context.contentResolver,
                uri,
                reqWidth.coerceAtLeast(1),
                reqHeight.coerceAtLeast(1)
            )
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM decoding $uri; clearing bitmap cache")
            bitmapCache.evictAll()
            null
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to decode $uri: ${throwable.message}")
            null
        } ?: run {
            failedUriLoads[cacheKey] = System.currentTimeMillis()
            return null
        }

        failedUriLoads.remove(cacheKey)
        bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clearIconCache() {
        bitmapCache.evictAll()
        failedUriLoads.clear()
    }

    fun evictUri(uri: Uri, reqWidth: Int, reqHeight: Int) {
        val cacheKey = "uri:$uri:$reqWidth:$reqHeight"
        bitmapCache.remove(cacheKey)
        failedUriLoads.remove(cacheKey)
    }

    fun evictUri(uri: Uri) {
        val cacheKeyPrefix = "uri:$uri:"
        bitmapCache.snapshot().keys
            .filter { it.startsWith(cacheKeyPrefix) }
            .forEach(bitmapCache::remove)
        failedUriLoads.keys
            .filter { it.startsWith(cacheKeyPrefix) }
            .forEach(failedUriLoads::remove)
    }

    fun evictFailedUri(uri: Uri) {
        val cacheKeyPrefix = "uri:$uri:"
        failedUriLoads.keys
            .filter { it.startsWith(cacheKeyPrefix) }
            .forEach(failedUriLoads::remove)
    }


    private fun decodeSampledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(
                        calculateInSampleSize(
                            info.size.width,
                            info.size.height,
                            reqWidth,
                            reqHeight
                        )
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.map { scaleBitmapIfNeeded(it, reqWidth, reqHeight) }.getOrNull()
        } else {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return@runCatching null
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        options.outWidth,
                        options.outHeight,
                        reqWidth,
                        reqHeight
                    )
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }.map { bitmap ->
                bitmap?.let { scaleBitmapIfNeeded(it, reqWidth, reqHeight) }
            }.getOrNull()
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, reqWidth: Int, reqHeight: Int): Bitmap {
        if (bitmap.width <= reqWidth && bitmap.height <= reqHeight) {
            return bitmap
        }

        val scale = minOf(
            reqWidth.toFloat() / bitmap.width,
            reqHeight.toFloat() / bitmap.height
        )
        val scaledBitmap = bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        return scaledBitmap
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (width <= 0 || height <= 0) {
            return 1
        }

        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }
}
