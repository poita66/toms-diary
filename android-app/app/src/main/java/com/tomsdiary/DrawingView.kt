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
    private var einkController: Any? = null
    private var dirtyRect: Rect? = null
    private var isSupernote = false



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
        guideLinesDirty = true  // Regenerate guide lines cache
        invalidate()
    }
    
    fun setReadOnly(readOnly: Boolean) {
        isReadOnly = readOnly
    }
    
    fun initEinkManager(context: Context) {
        // Detect if this is a Supernote device
        isSupernote = isSupernoteDevice(context)
        android.util.Log.d("DrawingView", "Is Supernote device: $isSupernote")
        
        if (!isSupernote) {
            android.util.Log.d("DrawingView", "Not a Supernote device, skipping ePaper optimization")
            return
        }
        
        try {
            // Use EinkPWCoreController which has postRectForPw for partial updates
            val className = "com.htfyun.eink.pw.core.EinkPWCoreController"
            val controllerClass = Class.forName(className)
            val constructor = controllerClass.getDeclaredConstructor()
            einkController = constructor.newInstance()
            
            // Initialize the native library
            val initMethod = controllerClass.getDeclaredMethod("initForPw")
            initMethod.isAccessible = true
            initMethod.invoke(einkController)
            
            // Enable fast ePaper update mode on this view
            // setEinkUpdateModeWithView(View view, int dataMode, int dispMode)
            val viewClass = Class.forName("android.view.View")
            val setModeMethod = controllerClass.getDeclaredMethod(
                "setEinkUpdateModeWithView",
                viewClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setModeMethod.isAccessible = true
            // dataMode=1 (fast), dispMode=1 (fast partial update)
            setModeMethod.invoke(einkController, this, 1, 1)
            android.util.Log.d("DrawingView", "Eink update mode set to fast partial")
            
            android.util.Log.d("DrawingView", "EinkPWCoreController initialized: $einkController")
        } catch (e: Exception) {
            android.util.Log.e("DrawingView", "Failed to init EinkPWCoreController", e)
            einkController = null
            isSupernote = false
        }
    }
    
    /**
     * Detect if this is a Supernote device.
     * Checks for Supernote-specific properties and classes.
     */
    private fun isSupernoteDevice(context: Context): Boolean {
        return try {
            // Check for Supernote's custom ePaper class
            Class.forName("com.htfyun.eink.pw.core.EinkPWCoreController")
            true
        } catch (e: Exception) {
            try {
                // Fallback: check device manufacturer/model
                val build = android.os.Build::class.java
                val manufacturer = build.getField("MANUFACTURER").get(null) as? String
                val model = build.getField("MODEL").get(null) as? String
                
                android.util.Log.d("DrawingView", "Device: $manufacturer / $model")
                
                // Supernote devices have "HTF" or "Supernote" in manufacturer/model
                (manufacturer?.contains("HTF", ignoreCase = true) == true ||
                 manufacturer?.contains("Supernote", ignoreCase = true) == true ||
                 model?.contains("SN", ignoreCase = true) == true)
            } catch (e2: Exception) {
                false
            }
        }
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

                // Initial invalidation for pen down
                invalidate()
                // Don't trigger ePaper refresh on down - wait for actual drawing
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

                    // Invalidate and queue ePaper update (without triggering immediately)
                    invalidate()
                    queuePartialRefresh()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    paths.add(currentPath)
                    pathColors.add(currentColor)
                    currentPath = Path()
                    // Final full invalidation and ePaper refresh
                    invalidate()
                    triggerPartialRefresh()
                    dirtyRect = null  // Reset dirty rect
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
                
                // Invalidate directly - no throttling
                invalidate()
                // Don't trigger ePaper refresh on generic motion either
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
    
    /**
     * Queue a partial refresh without triggering it immediately.
     * Only works on Supernote devices with custom ePaper API.
     */
    private fun queuePartialRefresh() {
        if (!isSupernote || einkController == null) return
        
        dirtyRect?.let { rect ->
            einkController?.let {
                try {
                    // Queue the dirty rect for partial update
                    val postRectMethod = it.javaClass.getDeclaredMethod(
                        "postRectForPw",
                        Rect::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    postRectMethod.isAccessible = true
                    // Parameters: bmpType=0, displayMode=1 (fast), dataMode=1 (fast), a2Gate=0
                    postRectMethod.invoke(it, rect, 0, 1, 1, 0)
                } catch (e1: Exception) {
                    android.util.Log.e("DrawingView", "Failed to queue partial refresh", e1)
                }
            }
        }
    }
    
    /**
     * Trigger the queued partial refresh.
     * Only works on Supernote devices with custom ePaper API.
     */
    private fun triggerPartialRefresh() {
        if (!isSupernote || einkController == null) {
            android.util.Log.d("DrawingView", "Not a Supernote device or ePaper API not available")
            return
        }
        
        einkController?.let {
            try {
                // Update PW property to trigger the actual display update
                val updatePropMethod = it.javaClass.getDeclaredMethod("updatePWProperty")
                updatePropMethod.isAccessible = true
                updatePropMethod.invoke(it)
            } catch (e1: Exception) {
                android.util.Log.e("DrawingView", "Failed to trigger partial refresh", e1)
            }
        }
    }
}
