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
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaThumbnailLoader {
    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

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
        bitmapCache.get(cacheKey)?.let { return it }

        val bitmap = decodeSampledBitmap(
            context.contentResolver,
            uri,
            reqWidth.coerceAtLeast(1),
            reqHeight.coerceAtLeast(1)
        ) ?: return null

        bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clearIconCache() {
        bitmapCache.evictAll()
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
