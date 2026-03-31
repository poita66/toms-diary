package com.tomsdiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val paths = mutableListOf<Path>()
    private val pathColors = mutableListOf<Int>()
    private var currentPath = Path()
    private var currentColor = Color.BLACK
    private var isDrawing = false

    private val responses = mutableListOf<Triple<Bitmap, Matrix, Paint>>()
    private val words = mutableListOf<Triple<Bitmap, Matrix, Paint>>()
    private val GUIDE_LINE_Y_SPACING = 150f
    private val TOP_PADDING = 80f
    private val FIRST_LINE_Y = 180f
    private val LEFT_PADDING = 40f
    private val RIGHT_PADDING = 40f
    private var currentWordX = 0f
    private var currentWordY = 0f
    private var wordInitialized = false
    private var screenWidth = 0f
    private var isReadOnly = false

    private val handler = Handler(Looper.getMainLooper())
    private val throttleDelay = 5L // 5ms while writing for smoothness
    private var isThrottled = false
    private val refreshRunnable = Runnable {
        if (isDrawing) {
            invalidate()
        }
        isThrottled = false
    }

    fun clear() {
        paths.clear()
        pathColors.clear()
        currentPath = Path()
        responses.clear()
        words.clear()
        currentWordX = 0f
        currentWordY = 0f
        wordInitialized = false
        isReadOnly = false
        invalidate()
    }
    
    fun setReadOnly(readOnly: Boolean) {
        isReadOnly = readOnly
    }

    fun getBitmap(): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun getHandwritingBounds(): android.graphics.Rect? {
        if (paths.isEmpty()) return null
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        paths.forEach { path ->
            val bounds = android.graphics.RectF()
            path.computeBounds(bounds, true)
            minX = minOf(minX, bounds.left)
            minY = minOf(minY, bounds.top)
            maxX = maxOf(maxX, bounds.right)
            maxY = maxOf(maxY, bounds.bottom)
        }
        
        return android.graphics.Rect(
            (minX).toInt(),
            (minY).toInt(),
            (maxX).toInt(),
            (maxY).toInt()
        )
    }

    fun drawOnBitmap(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        paths.forEachIndexed { index, path ->
            strokePaint.color = pathColors[index]
            canvas.drawPath(path, strokePaint)
        }
        
        words.forEach { (wordBitmap, matrix, paint) ->
            canvas.drawBitmap(wordBitmap, matrix, paint)
        }
    }

    fun updateBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint) {
        responses.add(Triple(bitmap, matrix, paint))
        invalidate()
    }

    fun addWord(bitmap: Bitmap, x: Float, y: Float) {
        val matrix = Matrix()
        matrix.postTranslate(x, y)
        words.add(Triple(bitmap, matrix, android.graphics.Paint().apply { isAntiAlias = true }))
        invalidate()
    }

    fun resetWordPosition() {
        currentWordX = LEFT_PADDING
        currentWordY = FIRST_LINE_Y
        wordInitialized = true
    }

    fun getNextWordPosition(): Pair<Float, Float> {
        if (!wordInitialized) {
            currentWordX = LEFT_PADDING
            currentWordY = FIRST_LINE_Y
            wordInitialized = true
        }
        return Pair(currentWordX, currentWordY)
    }

    fun advanceWordPosition(wordWidth: Float) {
        currentWordX += wordWidth + 10f
        val wrapWidth = if (screenWidth > 0) screenWidth - LEFT_PADDING - RIGHT_PADDING else 1200f - 80f
        if (currentWordX + wordWidth > wrapWidth) {
            currentWordX = LEFT_PADDING
            currentWordY += GUIDE_LINE_Y_SPACING
        }
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isStylusEvent(event)) {
            return handleStylusEvent(event)
        }
        
        if (isFingerEvent(event)) {
            return handleFingerEvent(event)
        }
        
        return false
    }
    
    private fun handleFingerEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (words.isNotEmpty() || responses.isNotEmpty()) {
                    clear()
                    (context as? MainActivity)?.resetWordPosition()
                    (context as? MainActivity)?.navigateToNewestPage()
                }
                return true
            }
        }
        return false
    }
    
    private fun handleStylusEvent(event: MotionEvent): Boolean {
        if (isReadOnly) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (words.isNotEmpty() || responses.isNotEmpty()) {
                    clear()
                    (context as? MainActivity)?.resetWordPosition()
                    (context as? MainActivity)?.navigateToNewestPage()
                }
                
                isDrawing = true
                currentPath = Path()
                lastX = event.x
                lastY = event.y
                currentPath.moveTo(event.x, event.y)
                currentColor = Color.BLACK

                handler.removeCallbacks(refreshRunnable)
                isThrottled = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentPath.quadTo(lastX, lastY, (event.x + lastX) / 2, (event.y + lastY) / 2)
                    lastX = event.x
                    lastY = event.y

                    if (!isThrottled) {
                        isThrottled = true
                        handler.postDelayed(refreshRunnable, throttleDelay)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    isThrottled = false
                    handler.removeCallbacks(refreshRunnable)
                    paths.add(currentPath)
                    pathColors.add(currentColor)
                    currentPath = Path()
                    invalidate()
                    (context as? MainActivity)?.scheduleAutoSend()
                }
            }
        }
        return true
    }
    
    private fun isFingerEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        return toolType == MotionEvent.TOOL_TYPE_FINGER
    }

    private fun isStylusEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_MOUSE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        screenWidth = width.toFloat()
        
        canvas.drawColor(Color.WHITE)
        
        drawGuideLines(canvas)
        
        responses.forEach { (bitmap, matrix, paint) ->
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        
        words.forEach { (bitmap, matrix, paint) ->
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        
        paths.forEachIndexed { index, path ->
            strokePaint.color = pathColors[index]
            canvas.drawPath(path, strokePaint)
        }
        
        if (isDrawing && !currentPath.isEmpty) {
            strokePaint.color = currentColor
            canvas.drawPath(currentPath, strokePaint)
        }
    }
    
    private fun drawGuideLines(canvas: Canvas) {
        var y = FIRST_LINE_Y
        val maxY = height.toFloat()
        val lineWidth = if (screenWidth > 0) screenWidth - LEFT_PADDING - RIGHT_PADDING else 1200f - 80f
        
        while (y < maxY) {
            canvas.drawLine(LEFT_PADDING, y, LEFT_PADDING + lineWidth, y, linePaint)
            y += GUIDE_LINE_Y_SPACING
        }
    }
}
