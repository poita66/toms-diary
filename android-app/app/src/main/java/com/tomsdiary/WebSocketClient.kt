package com.tomsdiary

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

data class WebSocketMessage(
    val type: String,
    val data: String?,
    val metadata: JsonObject? = null
)

class WebSocketClient(private val serverUri: String, private val listener: WebSocketListener) :
    WebSocketClient(URI(serverUri)) {

    interface WebSocketListener {
        fun onOpen()
        fun onClose(code: Int, reason: String, remote: Boolean)
        fun onError(ex: Exception)
        fun onClear()
        fun onRenderChunk(data: String, metadata: JsonObject?)
        fun onToken(token: String)
        fun onComplete()
    }

    private val gson = Gson()
    private var isSending = false

    override fun onOpen(handshaked: ServerHandshake) {
        android.util.Log.d("WebSocketClient", "onOpen: connected to $serverUri")
        listener.onOpen()
    }

    override fun onMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "clear" -> {
                    listener.onClear()
                }
                "render-chunk" -> {
                    val data = json.get("data")?.asString
                    val metadata = json.get("metadata") as? JsonObject
                    listener.onRenderChunk(data ?: "", metadata)
                }
                "token" -> {
                    val data = json.get("data")?.asString
                    if (data != null) {
                        listener.onToken(data)
                    }
                }
                "complete" -> {
                    listener.onComplete()
                }
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        android.util.Log.d("WebSocketClient", "onClose: code=$code, reason=$reason, remote=$remote")
        listener.onClose(code, reason, remote)
    }

    override fun onError(ex: Exception) {
        android.util.Log.e("WebSocketClient", "onError: ${ex.message}", ex)
        listener.onError(ex)
    }

    fun sendImage(base64Image: String, screenWidth: Int, imageWidth: Int, imageHeight: Int, conversationHistory: List<Pair<String, String>> = emptyList(), persona: String = "tom") {
        if (isSending) return
        isSending = true

        try {
            val metadata = JsonObject().apply {
                addProperty("timestamp", System.currentTimeMillis())
                addProperty("screenWidth", screenWidth)
                addProperty("width", imageWidth)
                addProperty("height", imageHeight)
                addProperty("format", "png")
                addProperty("persona", persona)
            }

            val historyArray = com.google.gson.JsonArray()
            android.util.Log.d("WebSocketClient", "Sending history with ${conversationHistory.size} turns")
            conversationHistory.forEachIndexed { index, (userImage, responseText) ->
                android.util.Log.d("WebSocketClient", "History turn $index: userImage=${userImage.length} chars, responseText=${responseText.length} chars")
                val turn = JsonObject().apply {
                    addProperty("user", userImage)
                    addProperty("assistant", responseText)
                }
                historyArray.add(turn)
            }

            val message = JsonObject().apply {
                addProperty("type", "image")
                addProperty("data", base64Image)
                add("metadata", metadata)
                add("history", historyArray)
            }

            send(message.toString())
        } finally {
            isSending = false
        }
    }

    override fun connect() {
        android.util.Log.d("WebSocketClient", "connect() called for $serverUri")
        try {
            super.connect()
        } catch (e: Exception) {
            android.util.Log.e("WebSocketClient", "connect() failed: ${e.message}", e)
            listener.onError(e)
        }
    }

    fun disconnect() {
        try {
            close()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun cancel() {
        try {
            val message = JsonObject().apply {
                addProperty("type", "cancel")
            }
            send(message.toString())
        } catch (e: Exception) {
            android.util.Log.e("WebSocketClient", "cancel() failed: ${e.message}", e)
        }
    }
}
