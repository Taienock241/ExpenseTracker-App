package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Square, draggable and pinch-zoom crop area for the circular profile avatar. */
class ProfilePhotoCropView(context: Context, private val source: Bitmap) : FrameLayout(context) {
    private val photo = ImageView(context)
    private val transform = Matrix()
    private val matrixValues = FloatArray(9)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        alpha = 210
    }
    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        alpha = 100
    }
    private var baseScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            transform.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            val targetScale = (currentScale * detector.scaleFactor).coerceIn(baseScale, baseScale * 4f)
            val factor = targetScale / currentScale
            transform.postScale(factor, factor, detector.focusX, detector.focusY)
            applyTransform()
            return true
        }
    })

    init {
        isClickable = true
        contentDescription = "Move and zoom the photo to center it in the circular guide"
        photo.scaleType = ImageView.ScaleType.MATRIX
        photo.setImageBitmap(source)
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        baseScale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        transform.setScale(baseScale, baseScale)
        transform.postTranslate(
            (width - source.width * baseScale) / 2f,
            (height - source.height * baseScale) / 2f,
        )
        applyTransform()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    transform.postTranslate(event.x - lastX, event.y - lastY)
                    applyTransform()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> return true
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - dp(2f)
        canvas.drawCircle(centerX, centerY, radius, guidePaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, guideLinePaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, guideLinePaint)
    }

    fun croppedBitmap(): Bitmap {
        transform.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X].coerceAtLeast(0.0001f)
        val translateX = matrixValues[Matrix.MTRANS_X]
        val translateY = matrixValues[Matrix.MTRANS_Y]
        val side = (min(width, height) / scale).roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(min(source.width, source.height))
        val x = ((min(width, height) / 2f - translateX) / scale - side / 2f)
            .roundToInt().coerceIn(0, source.width - side)
        val y = ((min(width, height) / 2f - translateY) / scale - side / 2f)
            .roundToInt().coerceIn(0, source.height - side)
        return Bitmap.createBitmap(source, x, y, side, side)
    }

    private fun applyTransform() {
        transform.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val x = if (scaledWidth <= width) (width - scaledWidth) / 2f
        else matrixValues[Matrix.MTRANS_X].coerceIn(width - scaledWidth, 0f)
        val y = if (scaledHeight <= height) (height - scaledHeight) / 2f
        else matrixValues[Matrix.MTRANS_Y].coerceIn(height - scaledHeight, 0f)
        transform.setValues(matrixValues.apply {
            this[Matrix.MTRANS_X] = x
            this[Matrix.MTRANS_Y] = y
        })
        photo.imageMatrix = transform
        invalidate()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
