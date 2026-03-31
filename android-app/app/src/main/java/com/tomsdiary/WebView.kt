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
import java.util.*

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

    private var bitmapToDraw: Bitmap? = null
    private var bitmapMatrix: Matrix? = null
    private var bitmapPaint: Paint? = null

    fun clear() {
        paths.clear()
        pathColors.clear()
        currentPath = Path()
        bitmapToDraw = null
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

    fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint) {
        bitmapToDraw = bitmap
        bitmapMatrix = matrix
        bitmapPaint = paint
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                currentPath = Path()
                currentPath.moveTo(event.x, event.y)
                currentColor = Color.BLACK
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentPath.lineTo(event.x, event.y)
                    invalidate()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        
        paths.forEachIndexed { index, path ->
            paint.color = pathColors[index]
            canvas.drawPath(path, paint)
        }
        
        if (isDrawing && !currentPath.isEmpty) {
            paint.color = currentColor
            canvas.drawPath(currentPath, paint)
        }

        if (bitmapToDraw != null && bitmapMatrix != null && bitmapPaint != null) {
            canvas.drawBitmap(bitmapToDraw!!, bitmapMatrix!!, bitmapPaint!!)
        }
    }
}
