package com.tomsdiary

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var canvasView: WebView
    private lateinit var btnClear: Button
    private lateinit var btnSend: Button
    private lateinit var btnConnect: Button
    private lateinit var statusText: TextView

    private var webSocketClient: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isConnected = false
    private var isProcessing = false

    private val SERVER_URL = "ws://localhost:18080"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        canvasView = findViewById(R.id.canvasView)
        btnClear = findViewById(R.id.btnClear)
        btnSend = findViewById(R.id.btnSend)
        btnConnect = findViewById(R.id.btnConnect)
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
    }

    private fun toggleConnection() {
        if (isConnected) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun connect() {
        scope.launch {
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
                        }
                    }

                    override fun onError(ex: Exception) {
                        scope.launch {
                            Toast.makeText(this@MainActivity, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onRenderChunk(data: String, metadata: com.google.gson.JsonObject?) {
                        scope.launch {
                            if (!isProcessing) return@launch
                            val bytes = Base64.decode(data, Base64.NO_WRAP)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val scaledBitmap = scaleBitmapToFit(bitmap)
                            android.util.Log.d("MainActivity", "onRenderChunk: bitmap ${bitmap.width}x${bitmap.height}")
                            displayBitmap(scaledBitmap)
                        }
                    }

                    override fun onComplete() {
                        scope.launch {
                            isProcessing = false
                            btnSend.isEnabled = true
                            statusText.text = "Response complete"
                        }
                    }
                })

                webSocketClient?.connect()
            } catch (e: Exception) {
                isConnected = false
                updateConnectionState(false)
                Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disconnect() {
        webSocketClient?.disconnect()
        isConnected = false
        updateConnectionState(false)
    }

    private fun updateConnectionState(connected: Boolean) {
        btnConnect.text = if (connected) "Disconnect" else "Connect"
        statusText.text = if (connected) "Connected" else "Disconnected"
        statusText.setTextColor(if (connected) Color.GREEN else Color.RED)
        btnSend.isEnabled = connected && !isProcessing
    }

    private fun clearCanvas() {
        canvasView.clear()
    }

    private fun sendCanvasImage() {
        if (!isConnected) {
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isProcessing) {
            Toast.makeText(this, "Already processing", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        btnSend.isEnabled = false
        statusText.text = "Sending..."
        statusText.setTextColor(Color.BLUE)

        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = captureCanvas()
                val base64Image = bitmapToBase64(bitmap)
                
                webSocketClient?.sendImage(base64Image, bitmap.width, bitmap.height)
                
                withContext(Dispatchers.Main) {
                    clearCanvas()
                    statusText.text = "Waiting for response..."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    btnSend.isEnabled = true
                    statusText.text = "Error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun captureCanvas(): Bitmap {
        val bitmap = canvasView.getBitmap()
        canvasView.drawOnBitmap(bitmap)
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun displayBitmap(bitmap: Bitmap) {
        try {
            val matrix = Matrix()
            val bitmapWidth = bitmap.width.toFloat()
            val canvasWidth = canvasView.width.toFloat()
            val scaleX = canvasWidth / bitmapWidth
            
            val padding = 40f
            val translationX = padding
            val translationY = padding
            
            matrix.postScale(scaleX, scaleX)
            matrix.postTranslate(translationX, translationY)
            
            android.util.Log.d("MainActivity", "displayBitmap: ${bitmap.width}x${bitmap.height}, scale=$scaleX, pos=($translationX,$translationY)")
            canvasView.updateBitmap(bitmap, matrix, android.graphics.Paint().apply { isAntiAlias = true })
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to display", e)
            Toast.makeText(this, "Failed to display: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmapToFit(bitmap: Bitmap): Bitmap {
        val maxWidth = 1404
        val maxHeight = 1872
        
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        
        val scale = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        disconnect()
    }
}
