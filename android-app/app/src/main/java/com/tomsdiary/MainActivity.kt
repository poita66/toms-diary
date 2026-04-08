package com.tomsdiary

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var canvasView: DrawingView
    private lateinit var btnClear: Button
    private lateinit var btnSend: Button
    private lateinit var btnSettings: Button
    private lateinit var btnNewChat: Button
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    
    // LLM configuration loaded from preferences
    private lateinit var openAIClient: OpenAIClient
    private lateinit var handwritingRenderer: HandwritingRenderer

    private val AUTO_SEND_DELAY_MS = 2000L
    private var autoSendJob: Job? = null

    private var currentPersona = "tom"
    private lateinit var prefs: SharedPreferences

    private var currentHistoryIndex = -1
    private val historyCanvasBitmaps = mutableListOf<Bitmap?>()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("toms_diary", MODE_PRIVATE)
        loadHistory()
        
        // Initialize local services with configuration from preferences
        openAIClient = OpenAIClient(
            baseUrl = LLMConfig.getBaseUrl(this),
            apiKey = LLMConfig.getApiKey(this),
            model = LLMConfig.getModel(this)
        )
        handwritingRenderer = HandwritingRenderer(this)
        
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val swipeThreshold = 100f
                if (e2.x - e1.x > swipeThreshold && kotlin.math.abs(velocityY) < velocityX) {
                    navigateHistory(1)
                    return true
                }
                if (e1.x - e2.x > swipeThreshold && kotlin.math.abs(velocityY) < velocityX) {
                    navigateHistory(-1)
                    return true
                }
                return false
            }
        })
        
        initViews()
        setupListeners()
        setupGestureDetection()
        
        statusText.text = "Ready (local mode)"
        statusText.setTextColor(Color.GREEN)
    }

    private var conversationHistory = mutableListOf<Pair<String, String>>() // (fullUserMessage, assistantResponse)
    private var currentInputBitmap: String? = null
    private var currentResponseText = ""
    private var currentTranscription = ""

    private fun initViews() {
        canvasView = findViewById(R.id.canvasView)
        canvasView.initEinkManager(this)  // Initialize Supernote ePaper API
        btnClear = findViewById(R.id.btnClear)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        btnNewChat = findViewById(R.id.btnNewChat)
        statusText = findViewById(R.id.statusText)
        
        // Remove connect button - no longer needed in local mode
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        btnConnect.visibility = Button.GONE
    }

    private fun setupListeners() {
        btnClear.setOnClickListener {
            clearCanvas()
        }

        btnSend.setOnClickListener {
            sendCanvasImage()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnNewChat.setOnClickListener {
            startNewConversation()
        }
    }

    private fun setupGestureDetection() {
        canvasView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun startNewConversation() {
        currentHistoryIndex = -1
        conversationHistory.clear()
        historyCanvasBitmaps.clear()
        clearCanvas()
        currentTranscription = ""
        currentResponseText = ""
        saveHistory()
        statusText.text = "New conversation started"
    }

    private fun navigateHistory(direction: Int) {
        val targetIndex = if (direction > 0) historyCanvasBitmaps.size else currentHistoryIndex + direction

        if (targetIndex < 0 || targetIndex >= historyCanvasBitmaps.size) {
            return
        }

        currentHistoryIndex = targetIndex
        val bitmap = historyCanvasBitmaps[currentHistoryIndex]

        canvasView.clear()
        
        if (bitmap != null) {
            val matrix = Matrix()
            canvasView.updateBitmap(bitmap, matrix, android.graphics.Paint())
        }

        canvasView.setReadOnly(currentHistoryIndex >= 0)

        if (currentHistoryIndex < 0) {
            statusText.text = "New message (swipe to view history)"
        } else {
            statusText.text = "Message $currentHistoryIndex of ${historyCanvasBitmaps.size - 1}"
        }
    }

    fun navigateToNewestPage() {
        if (currentHistoryIndex < 0) return
        currentHistoryIndex = -1
        canvasView.clear()
        canvasView.setReadOnly(false)
        statusText.text = "New message"
    }

    fun resetWordPosition() {
        canvasView.resetWordPosition()
    }

    private fun clearCanvas() {
        cancelAutoSend()
        canvasView.clear()
        canvasView.resetWordPosition()
    }

    fun scheduleAutoSend() {
        if (isProcessing.get()) {
            // Cancel current processing
            isProcessing.set(false)
            btnSend.isEnabled = true
            statusText.text = "Request cancelled"
            return
        }
        cancelAutoSend()
        autoSendJob = scope.launch {
            delay(AUTO_SEND_DELAY_MS)
            sendCanvasImage()
        }
    }

    private fun cancelAutoSend() {
        autoSendJob?.cancel()
        autoSendJob = null
    }

    private fun sendCanvasImage() {
        cancelAutoSend()

        // Atomic compare-and-set to prevent race conditions
        if (!isProcessing.compareAndSet(false, true)) {
            android.util.Log.d("MainActivity", "sendCanvasImage: already processing, ignoring")
            return
        }
        // Reset cancellation flag for new request
        isCancelled.set(false)

        android.util.Log.d("MainActivity", "sendCanvasImage: history size = ${conversationHistory.size}")
        
        btnSend.isEnabled = false
        statusText.text = "Sending..."
        statusText.setTextColor(Color.BLUE)
        canvasView.resetWordPosition()

        scope.launch(Dispatchers.IO) {
            try {
                val fullBitmap = captureCanvas()
                
                // Get dynamic handwriting bounds
                val bounds = canvasView.getHandwritingBounds()
                
                val cropLeft = if (bounds != null) bounds.left else 0
                val cropTop = if (bounds != null) bounds.top else 0
                val cropWidth = if (bounds != null) bounds.right - bounds.left else fullBitmap.width
                val cropHeight = if (bounds != null) bounds.bottom - bounds.top else fullBitmap.height
                
                val croppedBitmap = Bitmap.createBitmap(
                    fullBitmap,
                    cropLeft,
                    cropTop,
                    cropWidth,
                    cropHeight
                )
                
                val base64Image = bitmapToBase64(croppedBitmap)
                currentInputBitmap = base64Image
                
                android.util.Log.d("MainActivity", "Image sizes: cropped=${croppedBitmap.width}x${croppedBitmap.height}, base64 length=${base64Image.length}")
                
                // Send conversation history (text only) to provide context
                val historyToSend = conversationHistory.takeLast(10).toList()
                android.util.Log.d("MainActivity", "Sending ${historyToSend.size} turns of history to LLM")
                
                withContext(Dispatchers.Main) {
                    statusText.text = "Processing..."
                }
                
                // Process locally with OpenAI API
                processLocally(base64Image, fullBitmap.width, historyToSend)
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing.set(false)
                    btnSend.isEnabled = true
                    statusText.text = "Error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun processLocally(base64Image: String, screenWidth: Int, history: List<Pair<String, String>>) {
        scope.launch(Dispatchers.IO) {
            try {
                val fontSize = handwritingRenderer.calculateFontSize(screenWidth)
                android.util.Log.d("MainActivity", "Using font size: $fontSize for screen width: $screenWidth")
                
                var fullResponse = ""
                for (chunk in openAIClient.chatStreamWithImage(
                    sessionId = "local",
                    imageBase64 = base64Image,
                    history = history,
                    persona = currentPersona,
                    onCancel = { isCancelled.get() }
                )) {
                    val token = chunk.token
                    if (token != null) {
                        fullResponse += token
                        withContext(Dispatchers.Main) {
                            statusText.text = "Writing..."
                        }
                    }
                    if (chunk.isDone) break
                }
                
                // Extract transcription and response from full response
                val transcription = if (fullResponse.contains("[TRANSCRIPTION]") && fullResponse.contains("[/TRANSCRIPTION]")) {
                    fullResponse.substringAfter("[TRANSCRIPTION]").substringBefore("[/TRANSCRIPTION]").trim()
                } else { "" }
                
                val responseText = if (fullResponse.contains("[/TRANSCRIPTION]")) {
                    fullResponse.substringAfter("[/TRANSCRIPTION]").trim()
                } else if (fullResponse.contains("[TRANSCRIPTION]")) {
                    fullResponse.substringBefore("[TRANSCRIPTION]").trim()
                } else { fullResponse }
                
                currentResponseText = responseText
                currentTranscription = transcription
                
                android.util.Log.d("MainActivity", "Full response length: ${fullResponse.length}")
                android.util.Log.d("MainActivity", "Full response: '$fullResponse'")
                android.util.Log.d("MainActivity", "Transcription: '$transcription'")
                android.util.Log.d("MainActivity", "Response: '$responseText'")
                
                withContext(Dispatchers.Main) {
                    canvasView.clear()
                    canvasView.resetWordPosition()
                    renderResponseLocally(responseText, fontSize, screenWidth)
                    
                    isProcessing.set(false)
                    isCancelled.set(false)
                    if (isCancelled.get()) {
                        statusText.text = "Cancelled"
                    } else {
                        btnSend.isEnabled = true
                        statusText.text = "Response complete"
                        saveToHistory()
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing.set(false)
                    isCancelled.set(false)
                    btnSend.isEnabled = true
                    statusText.text = "Error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Processing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun renderResponseLocally(text: String, fontSize: Int, screenWidth: Int) {
        scope.launch(Dispatchers.Default) {
            // Calculate actual usable width: screen width minus left/right padding (40px each side)
            val maxWidth = screenWidth - 80
            
            val options = HandwritingRenderer.RenderOptions(
                fontSize = fontSize,
                backgroundColor = Color.WHITE,
                textColor = Color.BLACK,
                padding = 20,
                maxWidth = maxWidth,
                addVariation = true
            )
            
            for (wordResult in handwritingRenderer.renderWordStream(text, 0, options)) {
                withContext(Dispatchers.Main) {
                    displayWord(wordResult.bitmap)
                }
                // Small delay for smooth streaming effect
                delay(50)
            }
        }
    }

    private fun captureCanvas(): Bitmap {
        val bitmap = canvasView.getBitmap()
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvasView.drawOnBitmap(bitmap)
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Convert to greyscale
        val config = Bitmap.Config.ARGB_8888
        val greyscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
        val canvas = android.graphics.Canvas(greyscaleBitmap)
        
        // Draw white background
        canvas.drawColor(Color.WHITE)
        
        // Convert to greyscale using ColorMatrix
        val paint = android.graphics.Paint()
        val colorFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply {
                setSaturation(0f) // Remove all color, keep only luminance
            }
        )
        paint.colorFilter = colorFilter
        
        // Draw the original bitmap with greyscale filter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        val byteArrayOutputStream = ByteArrayOutputStream()
        greyscaleBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun displayWord(bitmap: Bitmap) {
        try {
            val (x, y) = canvasView.getNextWordPosition()
            // Offset y to align text baseline with the line
            // HandwritingRenderer uses topPadding=10, bottomPadding=25
            // So baseline is roughly 75% from the top of the bitmap
            val baselineOffset = (bitmap.height * 0.75f)
            val adjustedY = y - baselineOffset
            canvasView.addWord(bitmap, x, adjustedY)
            canvasView.advanceWordPosition(bitmap.width.toFloat())
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to display word", e)
            Toast.makeText(this, "Failed to display: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToHistory() {
        if (currentTranscription.isNotEmpty()) {
            // Save full formatted message: [TRANSCRIPTION]...[/TRANSCRIPTION]\n\nresponse
            val fullUserMessage = "[TRANSCRIPTION]$currentTranscription[/TRANSCRIPTION]"
            conversationHistory.add(Pair(fullUserMessage, currentResponseText))
            android.util.Log.d("MainActivity", "Added to history: '$fullUserMessage' -> '${currentResponseText.take(50)}...'")
            
            val fullBitmap = captureCanvas()
            historyCanvasBitmaps.add(0, fullBitmap)
            
            saveHistory()
        }
        currentTranscription = ""
        currentResponseText = ""
    }

    private fun saveHistory() {
        val editor = prefs.edit()
        val historyList = conversationHistory.map { "${it.first}|${it.second}" }
        editor.putStringSet("history", historyList.toSet())
        editor.apply()
        android.util.Log.d("MainActivity", "Saved history to prefs, size: ${conversationHistory.size}")
    }

    private fun loadHistory() {
        val historySet = prefs.getStringSet("history", emptySet()) ?: emptySet()
        conversationHistory = mutableListOf()
        historySet.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                conversationHistory.add(Pair(parts[0], parts[1]))
            }
        }
        android.util.Log.d("MainActivity", "Loaded history from prefs, size: ${conversationHistory.size}")
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val personaGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.personaGroup)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val etBaseUrl = dialogView.findViewById<android.widget.EditText>(R.id.etBaseUrl)
        val etApiKey = dialogView.findViewById<android.widget.EditText>(R.id.etApiKey)
        val etModel = dialogView.findViewById<android.widget.EditText>(R.id.etModel)
        
        // Load current settings
        etBaseUrl.setText(LLMConfig.getBaseUrl(this))
        etApiKey.setText(LLMConfig.getApiKey(this))
        etModel.setText(LLMConfig.getModel(this))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            when (personaGroup.checkedRadioButtonId) {
                R.id.personaTom -> currentPersona = "tom"
                R.id.personaGeneric -> currentPersona = "generic"
                R.id.personaFriendly -> currentPersona = "friendly"
            }
            
            // Save LLM settings
            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()
            
            if (baseUrl.isNotEmpty()) {
                LLMConfig.setBaseUrl(this, baseUrl)
            }
            LLMConfig.setApiKey(this, apiKey)
            if (model.isNotEmpty()) {
                LLMConfig.setModel(this, model)
            }
            
            android.util.Log.d("MainActivity", "Settings saved: persona=$currentPersona, baseUrl=$baseUrl, model=$model")
            
            // Reinitialize OpenAI client with new settings
            openAIClient = OpenAIClient(
                baseUrl = LLMConfig.getBaseUrl(this),
                apiKey = LLMConfig.getApiKey(this),
                model = LLMConfig.getModel(this)
            )
            
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoSend()
        scope.cancel()
    }
}
