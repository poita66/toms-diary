package com.tomsdiary

import android.content.Context
import android.graphics.*
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class HandwritingRenderer(private val context: Context) {
    
    private var caveatFont: Typeface? = null
    
    init {
        loadFont()
    }
    
    private fun loadFont() {
        try {
            caveatFont = Typeface.createFromAsset(context.assets, "Caveat-Regular.ttf")
            android.util.Log.d("HandwritingRenderer", "Caveat font loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("HandwritingRenderer", "Failed to load Caveat font", e)
            caveatFont = Typeface.create("Caveat", Typeface.NORMAL)
        }
    }
    
    data class RenderOptions(
        val fontSize: Int = 90,
        val backgroundColor: Int = Color.WHITE,
        val textColor: Int = Color.BLACK,
        val padding: Int = 20,
        val maxWidth: Int = 900,
        val addVariation: Boolean = true
    )
    
    data class WordRenderResult(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val x: Float,
        val y: Float
    )
    
    fun calculateFontSize(screenWidth: Int): Int {
        return kotlin.math.max(48, kotlin.math.min(120, screenWidth / 14))
    }
    
    fun renderWord(word: String, options: RenderOptions = RenderOptions()): Bitmap {
        val font = caveatFont ?: Typeface.create("Caveat", Typeface.NORMAL)
        val paint = Paint().apply {
            typeface = font
            textSize = options.fontSize.toFloat()
            color = options.textColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        val fontMetrics = paint.fontMetrics
        
        val wordWidth = paint.measureText(word)
        val wordHeight = kotlin.math.abs(fontMetrics.bottom - fontMetrics.top)
        
        val leftPadding = 15f
        val rightPadding = 30f
        val topPadding = 10f
        val bottomPadding = 25f
        
        val canvasWidth = (wordWidth + leftPadding + rightPadding).toInt()
        val canvasHeight = (wordHeight + topPadding + bottomPadding).toInt()
        
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.drawColor(options.backgroundColor)
        
        val x = leftPadding
        val y = topPadding + wordHeight - fontMetrics.bottom
        
        if (options.addVariation) {
            val rotation = (Random.nextDouble() - 0.5).toFloat() * 0.02f
            val xOffset = (Random.nextDouble() - 0.5).toFloat() * 2f
            
            canvas.save()
            canvas.translate(x + xOffset, y)
            canvas.rotate((rotation * 180f / kotlin.math.PI).toFloat())
            canvas.drawText(word, 0f, 0f, paint)
            canvas.restore()
        } else {
            canvas.drawText(word, x, y, paint)
        }
        
        return bitmap
    }
    
    fun renderWordStream(
        text: String,
        screenWidth: Int,
        options: RenderOptions = RenderOptions(fontSize = calculateFontSize(screenWidth))
    ): Sequence<WordRenderResult> {
        val words = text.split(' ')
        var currentX = options.padding.toFloat()
        var currentY = (options.padding + options.fontSize).toFloat()
        val lineHeight = (options.fontSize * 1.5f)
        
        return sequence {
            for (word in words) {
                val bitmap = renderWord(word, options)
                
                yield(
                    WordRenderResult(
                        bitmap = bitmap,
                        width = bitmap.width,
                        height = bitmap.height,
                        x = currentX,
                        y = currentY
                    )
                )
                
                val wordWidth = paintWordWidth(word, options.fontSize)
                currentX += wordWidth + 10f
                
                if (currentX + wordWidth > options.maxWidth - options.padding) {
                    currentX = options.padding.toFloat()
                    currentY += lineHeight
                }
            }
        }
    }
    
    private fun paintWordWidth(word: String, fontSize: Int): Float {
        val font = caveatFont ?: Typeface.create("Caveat", Typeface.NORMAL)
        val paint = Paint().apply {
            typeface = font
            textSize = fontSize.toFloat()
        }
        return paint.measureText(word)
    }
    
    fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return byteArray.encodeBase64().decodeToString()
    }
    
    companion object {
        private fun ByteArray.encodeBase64(): ByteArray {
            return android.util.Base64.encode(this, android.util.Base64.NO_WRAP)
        }
    }
}
