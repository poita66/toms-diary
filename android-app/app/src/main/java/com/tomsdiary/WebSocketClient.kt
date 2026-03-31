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
        fun onRenderChunk(data: String, metadata: JsonObject?)
        fun onComplete()
    }

    private val gson = Gson()
    private var isSending = false

    override fun onOpen(handshaked: ServerHandshake) {
        listener.onOpen()
    }

    override fun onMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "render-chunk" -> {
                    val data = json.get("data")?.asString
                    val metadata = json.get("metadata") as? JsonObject
                    listener.onRenderChunk(data ?: "", metadata)
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
        listener.onClose(code, reason, remote)
    }

    override fun onError(ex: Exception) {
        listener.onError(ex)
    }

    fun sendImage(base64Image: String, width: Int, height: Int) {
        if (isSending) return
        isSending = true

        val metadata = JsonObject().apply {
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("width", width)
            addProperty("height", height)
            addProperty("format", "png")
        }

        val message = JsonObject().apply {
            addProperty("type", "image")
            addProperty("data", base64Image)
            add("metadata", metadata)
        }

        send(message.toString())
    }

    fun disconnect() {
        try {
            close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
