package com.yinxing.launcher.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import kotlin.math.max

object AvatarCropGeometry {
    fun baseScale(bitmapWidth: Int, bitmapHeight: Int, viewWidth: Int, viewHeight: Int): Float {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 1f
        return max(viewWidth.toFloat() / bitmapWidth, viewHeight.toFloat() / bitmapHeight)
    }

    fun clampOffset(offset: Float, drawnSize: Float, viewportSize: Float): Float {
        val limit = ((drawnSize - viewportSize) / 2f).coerceAtLeast(0f)
        return offset.coerceIn(-limit, limit)
    }
}

class AvatarCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        alpha = 220
    }
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                userScale = (userScale * detector.scaleFactor).coerceIn(1f, 4f)
                constrainOffsets()
                invalidate()
                return true
            }
        }
    )

    private var bitmap: Bitmap? = null
    private var userScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(value: Bitmap) {
        bitmap = value
        userScale = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(17, 24, 39))
        drawImage(canvas)
        val inset = borderPaint.strokeWidth / 2f
        val radius = resources.displayMetrics.density * 22f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                offsetX += event.x - lastX
                offsetY += event.y - lastY
                lastX = event.x
                lastY = event.y
                constrainOffsets()
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun createCroppedBitmap(outputSize: Int = 720): Bitmap {
        val output = createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.scale(outputSize.toFloat() / width, outputSize.toFloat() / height)
        drawImage(canvas)
        return output
    }

    private fun drawImage(canvas: Canvas) {
        val source = bitmap ?: return
        val scale = AvatarCropGeometry.baseScale(source.width, source.height, width, height) * userScale
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, -source.width / 2f, -source.height / 2f, imagePaint)
        canvas.restore()
    }

    private fun constrainOffsets() {
        val source = bitmap ?: return
        val scale = AvatarCropGeometry.baseScale(source.width, source.height, width, height) * userScale
        offsetX = AvatarCropGeometry.clampOffset(offsetX, source.width * scale, width.toFloat())
        offsetY = AvatarCropGeometry.clampOffset(offsetY, source.height * scale, height.toFloat())
    }
}
