package com.tomsdiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

class WebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val paths = mutableListOf<Path>()
    private val pathColors = mutableListOf<Int>()
    private var currentPath = Path()
    private var currentColor = Color.BLACK
    private var isDrawing = false

    private var responseBitmap: Bitmap? = null
    private var responseMatrix: Matrix? = null
    private var responsePaint: Paint? = null
    private var isBitmapSet = false

    private var choreographer: Choreographer? = null
    private var needsInvalidate = false
    private var frameCallback: Choreographer.FrameCallback? = null

    private fun requestAnimationFrame() {
        if (needsInvalidate) return
        needsInvalidate = true
        choreographer ?: Choreographer.getInstance().also { choreographer = it }
        frameCallback = Choreographer.FrameCallback {
            if (needsInvalidate) {
                needsInvalidate = false
                invalidate()
            }
            if (!isDrawing) {
                frameCallback = null
            } else if (needsInvalidate) {
                choreographer?.postFrameCallback(frameCallback!!)
            }
        }
        choreographer?.postFrameCallback(frameCallback!!)
    }

    fun clear() {
        paths.clear()
        pathColors.clear()
        currentPath = Path()
        responseBitmap?.recycle()
        responseBitmap = null
        responseMatrix = null
        responsePaint = null
        isBitmapSet = false
        invalidate()
    }

    fun getBitmap(): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun drawOnBitmap(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        paths.forEachIndexed { index, path ->
            paint.color = pathColors[index]
            canvas.drawPath(path, paint)
        }
    }

    fun updateBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint) {
        responseBitmap?.recycle()
        responseBitmap = bitmap
        responseMatrix = matrix
        responsePaint = paint
        isBitmapSet = true
        invalidate()
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isStylusEvent(event)) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                currentPath = Path()
                lastX = event.x
                lastY = event.y
                currentPath.moveTo(event.x, event.y)
                currentColor = Color.BLACK
                requestAnimationFrame()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentPath.quadTo(lastX, lastY, (event.x + lastX) / 2, (event.y + lastY) / 2)
                    lastX = event.x
                    lastY = event.y
                    requestAnimationFrame()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    paths.add(currentPath)
                    pathColors.add(currentColor)
                    currentPath = Path()
                }
            }
        }
        return true
    }

    private fun isStylusEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_MOUSE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isBitmapSet && responseBitmap != null && responseMatrix != null && responsePaint != null) {
            canvas.drawBitmap(responseBitmap!!, responseMatrix!!, responsePaint!!)
        } else {
            canvas.drawColor(Color.WHITE)
            
            paths.forEachIndexed { index, path ->
                paint.color = pathColors[index]
                canvas.drawPath(path, paint)
            }
            
            if (isDrawing && !currentPath.isEmpty) {
                paint.color = currentColor
                canvas.drawPath(currentPath, paint)
            }
        }
    }
}
