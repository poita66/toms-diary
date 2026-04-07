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
    private lateinit var btnConnect: Button
    private lateinit var btnSettings: Button
    private lateinit var btnNewChat: Button
    private lateinit var statusText: TextView

    private var webSocketClient: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isConnected = false
    private val isProcessing = AtomicBoolean(false)
    private var autoReconnectEnabled = true

    private val SERVER_URL = "ws://localhost:18080"
    private val AUTO_SEND_DELAY_MS = 2000L
    private var autoSendJob: Job? = null
    private var reconnectJob: Job? = null
    private val RECONNECT_DELAY_MS = 3000L
    private var clearAfterResponseJob: Job? = null
    private val CLEAR_AFTER_RESPONSE_DELAY_MS = 0L

    private var currentPersona = "tom"
    private var pendingClear = false
    private lateinit var prefs: SharedPreferences

    private var currentHistoryIndex = -1
    private val historyCanvasBitmaps = mutableListOf<Bitmap?>()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("toms_diary", MODE_PRIVATE)
        loadHistory()
        
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val swipeThreshold = 100f
                if (e2.x - e1.x > swipeThreshold && Math.abs(velocityY) < velocityX) {
                    navigateHistory(1)
                    return true
                }
                if (e1.x - e2.x > swipeThreshold && Math.abs(velocityY) < velocityX) {
                    navigateHistory(-1)
                    return true
                }
                return false
            }
        })
        
        initViews()
        setupListeners()
        setupGestureDetection()
        
        // Auto-connect after a short delay to ensure UI is ready
        scope.launch {
            delay(500)
            android.util.Log.d("MainActivity", "Auto-connecting to $SERVER_URL")
            connect()
        }
    }

    private var conversationHistory = mutableListOf<Pair<String, String>>()
    private var currentInputBitmap: String? = null
    private var currentResponseText = ""
    private var currentTranscription = ""

    private fun initViews() {
        canvasView = findViewById(R.id.canvasView)
        btnClear = findViewById(R.id.btnClear)
        btnSend = findViewById(R.id.btnSend)
        btnConnect = findViewById(R.id.btnConnect)
        btnSettings = findViewById(R.id.btnSettings)
        btnNewChat = findViewById(R.id.btnNewChat)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupListeners() {
        btnClear.setOnClickListener {
            clearCanvas()
        }

        btnSend.setOnClickListener {
            sendCanvasImage()
        }

        btnConnect.setOnClickListener {
            toggleConnection()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnNewChat.setOnClickListener {
            startNewConversation()
        }
    }

    private fun toggleConnection() {
        if (isConnected) {
            disconnect()
        } else {
            connect()
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
            val matrix = android.graphics.Matrix()
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

    private fun connect() {
        scope.launch {
            android.util.Log.d("MainActivity", "connect() called, isConnected=$isConnected")
            try {
                webSocketClient = WebSocketClient(SERVER_URL, object : WebSocketClient.WebSocketListener {
                    override fun onOpen() {
                        scope.launch {
                            isConnected = true
                            updateConnectionState(true)
                            Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onClose(code: Int, reason: String, remote: Boolean) {
                        scope.launch {
                            isConnected = false
                            updateConnectionState(false)
                            Toast.makeText(this@MainActivity, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
                            
                            if (autoReconnectEnabled) {
                                scheduleReconnect()
                            }
                        }
                    }

                    override fun onError(ex: Exception) {
                        scope.launch {
                            Toast.makeText(this@MainActivity, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onRenderChunk(data: String, metadata: com.google.gson.JsonObject?) {
                        scope.launch {
                            if (!isProcessing.get()) return@launch
                            
                            // First empty chunk signals to clear the canvas
                            if (data.isEmpty()) {
                                pendingClear = true
                                currentResponseText = ""
                                android.util.Log.d("MainActivity", "Clear signal received, waiting for first word")
                                return@launch
                            }
                            
                            // Clear canvas before displaying first word (only once)
                            if (pendingClear) {
                                android.util.Log.d("MainActivity", "Clearing canvas and resetting position")
                                canvasView.clear()
                                canvasView.resetWordPosition()
                                val (x, y) = canvasView.getNextWordPosition()
                                android.util.Log.d("MainActivity", "Position after reset: x=$x, y=$y")
                                pendingClear = false
                            }
                            
                            val bytes = Base64.decode(data, Base64.NO_WRAP)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            android.util.Log.d("MainActivity", "onRenderChunk: word ${bitmap.width}x${bitmap.height}")
                            displayWord(bitmap)
                            
                            // Cancel auto-clear while receiving words
                            cancelClearAfterResponse()
                        }
                    }

                    override fun onToken(token: String) {
                        scope.launch {
                            // Check if this is a transcription message (JSON)
                            try {
                                val json = com.google.gson.Gson().fromJson(token, com.google.gson.JsonObject::class.java)
                                if (json.has("type") && json.get("type").asString == "transcription") {
                                    currentTranscription = json.get("text").asString
                                    android.util.Log.d("MainActivity", "Received transcription: ${currentTranscription.length} chars")
                                    return@launch
                                }
                            } catch (e: Exception) {
                                // Not JSON, it's a regular token
                            }
                            currentResponseText += token
                        }
                    }

                    override fun onClear() {
                        // No longer used - clear happens on first word
                    }

                    override fun onComplete() {
                        android.util.Log.d("MainActivity", "onComplete() called")
                        scope.launch {
                            isProcessing.set(false)
                            btnSend.isEnabled = true
                            statusText.text = "Response complete"
                            android.util.Log.d("MainActivity", "Status set to: ${statusText.text}")
                            saveToHistory()
                            scheduleClearAfterResponse()
                        }
                    }
                })

                webSocketClient?.connect()
                android.util.Log.d("MainActivity", "Called webSocketClient.connect()")
            } catch (e: Exception) {
                isConnected = false
                updateConnectionState(false)
                Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disconnect() {
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        webSocketClient?.disconnect()
        isConnected = false
        isProcessing.set(false)
        updateConnectionState(false)
    }

    private fun scheduleReconnect() {
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            android.util.Log.d("MainActivity", "Auto-reconnecting...")
            autoReconnectEnabled = true
            connect()
        }
    }

    private fun updateConnectionState(connected: Boolean) {
        btnConnect.text = if (connected) "Disconnect" else "Connect"
        statusText.text = if (connected) "Connected" else "Disconnected"
        statusText.setTextColor(if (connected) Color.GREEN else Color.RED)
        btnSend.isEnabled = connected && !isProcessing.get()
    }

    private fun clearCanvas() {
        cancelClearAfterResponse()
        canvasView.clear()
        canvasView.resetWordPosition()
        cancelAutoSend()
    }

    fun scheduleAutoSend() {
        if (!isConnected || isProcessing.get()) {
            if (isProcessing.get()) {
                webSocketClient?.cancel()
                isProcessing.set(false)
                btnSend.isEnabled = true
                statusText.text = "Request cancelled"
            }
            return
        }
        cancelAutoSend()
        cancelClearAfterResponse()
        autoSendJob = scope.launch {
            delay(AUTO_SEND_DELAY_MS)
            sendCanvasImage()
        }
    }

    private fun cancelAutoSend() {
        autoSendJob?.cancel()
        autoSendJob = null
    }

    private fun scheduleClearAfterResponse() {
        // Don't auto-clear - wait for user to write or touch
    }

    private fun cancelClearAfterResponse() {
        // No-op - no auto-clear
    }

    private fun sendCanvasImage() {
        cancelAutoSend()

        if (!isConnected) {
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
            return
        }

        // Atomic compare-and-set to prevent race conditions
        if (!isProcessing.compareAndSet(false, true)) {
            android.util.Log.d("MainActivity", "sendCanvasImage: already processing, ignoring")
            return
        }

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
                
                // Send full screen width for font sizing, actual image dimensions
                // Limit history to last 3 turns to reduce inference time
                val historyToSend = conversationHistory.takeLast(3).toList()
                webSocketClient?.sendImage(base64Image, fullBitmap.width, croppedBitmap.width, croppedBitmap.height, historyToSend, currentPersona)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "Waiting for response..."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing.set(false)
                    btnSend.isEnabled = true
                    statusText.text = "Error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
        // Convert to greyscale (4-bit would be ideal but Android only supports 8-bit)
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
            // Offset y upward by bitmap height so text baseline sits on the line
            // Use descender offset (typically ~20% of height) instead of full height
            val descenderOffset = (bitmap.height * 0.2).toFloat()
            val adjustedY = y - descenderOffset
            canvasView.addWord(bitmap, x, adjustedY)
            canvasView.advanceWordPosition(bitmap.width.toFloat())
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to display word", e)
            Toast.makeText(this, "Failed to display: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    private fun saveToHistory() {
        if (currentTranscription.isNotEmpty()) {
            conversationHistory.add(Pair(currentTranscription, currentResponseText))
            android.util.Log.d("MainActivity", "Added to history, total: ${conversationHistory.size}")
            
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
            android.util.Log.d("MainActivity", "Persona set to: $currentPersona")
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoSend()
        cancelClearAfterResponse()
        scope.cancel()
        disconnect()
    }
}
