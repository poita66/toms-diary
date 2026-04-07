package com.tomsdiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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

    // Cache for static elements to avoid redrawing on every frame
    private var guideLinesBitmap: Bitmap? = null
    private var guideLinesDirty = true

    private val handler = Handler(Looper.getMainLooper())
    private val throttleDelay = 20L // Increased from 5ms to 20ms for better balance
    private var isThrottled = false
    private var dirtyRect: Rect? = null // Track area that needs redrawing for partial updates
    
    private val refreshRunnable = Runnable {
        if (isDrawing) {
            // Use partial invalidation for faster updates (<50ms vs 500ms+ for full refresh)
            dirtyRect?.let { rect ->
                invalidate(rect)
            } ?: invalidate()
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
        dirtyRect = null
        guideLinesDirty = true  // Regenerate guide lines cache
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

                // Initialize dirty rect for partial updates
                dirtyRect = Rect(
                    (event.x - 20).toInt(),
                    (event.y - 20).toInt(),
                    (event.x + 20).toInt(),
                    (event.y + 20).toInt()
                )

                handler.removeCallbacks(refreshRunnable)
                isThrottled = false
                // Initial invalidation for pen down
                dirtyRect?.let { invalidate(it) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentPath.quadTo(lastX, lastY, (event.x + lastX) / 2, (event.y + lastY) / 2)
                    lastX = event.x
                    lastY = event.y

                    // Expand dirty rect to include new stroke segment
                    dirtyRect?.let { rect ->
                        rect.left = minOf(rect.left, (minOf(lastX, event.x) - 20).toInt())
                        rect.top = minOf(rect.top, (minOf(lastY, event.y) - 20).toInt())
                        rect.right = maxOf(rect.right, (maxOf(lastX, event.x) + 20).toInt())
                        rect.bottom = maxOf(rect.bottom, (maxOf(lastY, event.y) + 20).toInt())
                    }

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
                    // Reset dirty rect for next stroke
                    dirtyRect = null
                    // Final full invalidation to ensure everything is rendered
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
    
    /**
     * Handle generic motion events for lower-latency stylus input.
     * This can provide faster response than standard onTouchEvent.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Handle stylus motion events with lower latency
        val toolType = event.getToolType(0)
        if ((toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_MOUSE) 
            && event.action == MotionEvent.ACTION_MOVE && !isReadOnly) {
            if (isDrawing) {
                currentPath.quadTo(lastX, lastY, (event.x + lastX) / 2, (event.y + lastY) / 2)
                lastX = event.x
                lastY = event.y
                
                // Update dirty rect
                dirtyRect?.let { rect ->
                    rect.left = minOf(rect.left, (minOf(lastX, event.x) - 20).toInt())
                    rect.top = minOf(rect.top, (minOf(lastY, event.y) - 20).toInt())
                    rect.right = maxOf(rect.right, (maxOf(lastX, event.x) + 20).toInt())
                    rect.bottom = maxOf(rect.bottom, (maxOf(lastY, event.y) + 20).toInt())
                }
                
                // Invalidate with partial update for faster response
                dirtyRect?.let { invalidate(it) }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Guide lines need to be regenerated when view size changes
        if (w != oldw || h != oldh) {
            guideLinesDirty = true
        }
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
    
    /**
     * Draw guide lines using cached bitmap for better performance.
     * Guide lines are pre-rendered and only regenerated when needed.
     */
    private fun drawGuideLines(canvas: Canvas) {
        // Use cached bitmap if available and not dirty
        if (!guideLinesDirty && guideLinesBitmap != null) {
            canvas.drawBitmap(guideLinesBitmap!!, 0f, 0f, null)
            return
        }
        
        // Create or regenerate the guide lines bitmap
        guideLinesBitmap = createGuideLinesBitmap()
        guideLinesDirty = false
        
        // Draw the cached bitmap
        canvas.drawBitmap(guideLinesBitmap!!, 0f, 0f, null)
    }
    
    /**
     * Pre-render guide lines to a bitmap for caching.
     * This avoids redrawing the same lines on every frame.
     */
    private fun createGuideLinesBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        var y = FIRST_LINE_Y
        val maxY = height.toFloat()
        val lineWidth = if (screenWidth > 0) screenWidth - LEFT_PADDING - RIGHT_PADDING else 1200f - 80f
        
        while (y < maxY) {
            canvas.drawLine(LEFT_PADDING, y, LEFT_PADDING + lineWidth, y, linePaint)
            y += GUIDE_LINE_Y_SPACING
        }
        
        return bitmap
    }
}
