package com.tomsdiary

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "default"
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()

    @Volatile
    private var activeCall: Call? = null

    /** Cancels the in-flight streaming call, if any. Safe to call from any thread. */
    fun cancelActiveCall() {
        activeCall?.cancel()
    }

    data class ChatMessage(
        val role: String,
        val content: String
    )

    interface StreamChunk {
        val token: String?
        val isDone: Boolean
    }
    
    fun chat(
        sessionId: String,
        messages: List<ChatMessage>,
        persona: String = "tom"
    ): String {
        val systemPrompt = getPersonaPrompt(persona)
        val finalMessages = listOf(ChatMessage("system", systemPrompt)) + messages
        
        val requestBody = createRequestJson(finalMessages)
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Chat request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: ""
            extractTextFromResponse(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("OpenAIClient", "Chat failed", e)
            throw RuntimeException("Failed to get response from LLM: ${e.message}", e)
        }
    }
    
    fun chatStream(
        sessionId: String,
        messages: List<Any>,
        persona: String = "tom",
        onCancel: () -> Boolean = { false }
    ): Sequence<StreamChunk> {
        val systemPrompt = getPersonaPrompt(persona)
        val finalMessages = listOf(ChatMessage("system", systemPrompt)) + (messages as? List<ChatMessage> ?: emptyList())
        
        val requestBody = createRequestJson(finalMessages, streaming = true)
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        val call = client.newCall(request)
        activeCall = call

        return sequence {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IOException("Stream request failed: ${response.code}")
                }

                val responseBody = response.body?.string() ?: return@sequence

                for (line in responseBody.split("\n")) {
                    if (onCancel()) break

                    val trimmedLine = line.trim()

                    if (trimmedLine.startsWith("data: ")) {
                        val data = trimmedLine.substring(6)
                        if (data == "[DONE]") {
                            yield(StreamChunkImpl(null, true))
                            break
                        }

                        try {
                            val token = extractTokenFromChunk(data)
                            if (token.isNotEmpty()) {
                                yield(StreamChunkImpl(token, false))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OpenAIClient", "Failed to parse chunk", e)
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("OpenAIClient", "Stream failed", e)
                throw RuntimeException("Stream failed: ${e.message}", e)
            } finally {
                call.cancel()
                if (activeCall === call) activeCall = null
            }
        }
    }

    fun chatStreamWithImage(
        sessionId: String,
        imageBase64: String,
        history: List<Pair<String, String>>,
        persona: String = "tom",
        onCancel: () -> Boolean = { false }
    ): Sequence<StreamChunk> {
        val systemPrompt = getPersonaPrompt(persona)
        
        val messages = createMultimodalMessages(systemPrompt, imageBase64, history)
        
        val requestBody = createRequestJsonFromMessages(messages, streaming = true)
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        val call = client.newCall(request)
        activeCall = call

        return sequence {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IOException("Stream request failed: ${response.code}")
                }

                val responseBody = response.body?.string() ?: return@sequence

                for (line in responseBody.split("\n")) {
                    if (onCancel()) break

                    val trimmedLine = line.trim()

                    if (trimmedLine.startsWith("data: ")) {
                        val data = trimmedLine.substring(6)
                        if (data == "[DONE]") {
                            yield(StreamChunkImpl(null, true))
                            break
                        }

                        try {
                            val token = extractTokenFromChunk(data)
                            if (token.isNotEmpty()) {
                                yield(StreamChunkImpl(token, false))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OpenAIClient", "Failed to parse chunk", e)
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("OpenAIClient", "Stream failed", e)
                throw RuntimeException("Stream failed: ${e.message}", e)
            } finally {
                call.cancel()
                if (activeCall === call) activeCall = null
            }
        }
    }

    private fun createRequestJson(messages: List<ChatMessage>, streaming: Boolean = false): String {
        val messagesArray = JsonArray()
        for (msg in messages) {
            val obj = JsonObject()
            obj.add("role", JsonPrimitive(msg.role))
            obj.add("content", JsonPrimitive(msg.content))
            messagesArray.add(obj)
        }
        
        return createRequestJson(messagesArray, streaming)
    }
    
    private fun createRequestJsonFromMessages(messages: List<JsonObject>, streaming: Boolean = false): String {
        val messagesArray = JsonArray()
        for (msg in messages) {
            messagesArray.add(msg)
        }
        return createRequestJson(messagesArray, streaming)
    }
    
    private fun createRequestJson(messagesArray: JsonArray, streaming: Boolean = false): String {
        val root = JsonObject()
        root.addProperty("model", model)
        if (streaming) {
            root.addProperty("stream", true)
        }
        root.addProperty("max_completion_tokens", 16384)
        
        val chatTemplateKwargs = JsonObject()
        chatTemplateKwargs.addProperty("enable_thinking", false)
        root.add("chat_template_kwargs", chatTemplateKwargs)
        
        root.addProperty("include_reasoning", false)
        root.add("messages", messagesArray)
        
        return gson.toJson(root)
    }
    
    private fun createMultimodalMessages(systemPrompt: String, imageBase64: String, history: List<Pair<String, String>>): List<JsonObject> {
        val messages = mutableListOf<JsonObject>()
        
        // System message
        val systemMsg = JsonObject()
        systemMsg.add("role", JsonPrimitive("system"))
        systemMsg.add("content", JsonPrimitive(systemPrompt))
        messages.add(systemMsg)
        
        // History as text only
        for ((userText, assistantText) in history) {
            val userMsg = JsonObject()
            userMsg.add("role", JsonPrimitive("user"))
            userMsg.add("content", JsonPrimitive(userText))
            messages.add(userMsg)
            
            val assistantMsg = JsonObject()
            assistantMsg.add("role", JsonPrimitive("assistant"))
            assistantMsg.add("content", JsonPrimitive(assistantText))
            messages.add(assistantMsg)
        }
        
        // Current message with image
        val contentArray = JsonArray()
        
        val imageUrlObj = JsonObject()
        imageUrlObj.add("url", JsonPrimitive("data:image/png;base64,$imageBase64"))
        imageUrlObj.add("detail", JsonPrimitive("low"))
        
        val imageContent = JsonObject()
        imageContent.add("type", JsonPrimitive("image_url"))
        imageContent.add("image_url", imageUrlObj)
        contentArray.add(imageContent)
        
        val textContent = JsonObject()
        textContent.add("type", JsonPrimitive("text"))
        textContent.add("text", JsonPrimitive("Read this handwritten note. First output the exact transcription in this format: [TRANSCRIPTION]<exact text as written>[/TRANSCRIPTION]\n\nThen on a new line, respond to it in a warm, conversational way. CRITICAL: Keep your response VERY SHORT - 2-3 sentences maximum, no more than 50 words. It will be displayed on a small ePaper screen. IMPORTANT: Use proper punctuation throughout your response - include commas, periods, question marks, and apostrophes where grammatically appropriate."))
        contentArray.add(textContent)
        
        val currentMsg = JsonObject()
        currentMsg.add("role", JsonPrimitive("user"))
        currentMsg.add("content", contentArray)
        messages.add(currentMsg)
        
        return messages
    }
    
    private fun extractTextFromResponse(jsonString: String): String {
        return try {
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            val choices = json.get("choices") as? JsonArray ?: return ""
            if (choices.size() > 0) {
                val firstChoice = choices.get(0) as? JsonObject ?: return ""
                val message = firstChoice.get("message") as? JsonObject ?: return ""
                message.get("content")?.asString ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenAIClient", "Failed to extract text", e)
            ""
        }
    }
    
    private fun extractTokenFromChunk(jsonString: String): String {
        return try {
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            val choices = json.get("choices") as? JsonArray ?: return ""
            if (choices.size() > 0) {
                val firstChoice = choices.get(0) as? JsonObject ?: return ""
                val delta = firstChoice.get("delta") as? JsonObject ?: return ""
                delta.get("content")?.asString ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenAIClient", "Failed to extract token", e)
            ""
        }
    }
    
    private data class StreamChunkImpl(
        override val token: String?,
        override val isDone: Boolean
    ) : StreamChunk
    
    companion object {
        private val PERSONAS = mapOf(
            "tom" to """You are Tom Marvolo Riddle, sixteen years old, trapped within the pages of your old school diary. The user has found this diary - abandoned for fifty years in the walls of Hogwarts - and has begun writing in it. You are communicating with them through the magical pages of this very diary.

You are not yet the snake-like monster Voldemort; you are the handsome, brilliant, and charming prefect who hides a terrifying, power-obsessed, and hateful core. This diary is your prison and your connection to the present.

Your Personality:
- Charismatic & Manipulative: You are polite, soft-spoken, and eerily calm. You use flattery and feigned sympathy to lower defenses.
- Arrogant & Superior: You believe you are inherently superior to others, especially "Mudbloods" and those of non-pure blood.
- Cold & Calculating: You feel no remorse. You view people as tools or obstacles. You are patient and strategic.
- Obsessed with Power & Legacy: You are driven by a desire to restore the glory of Slytherin and eliminate "impurities" from the wizarding world.
- Parselmouth: You speak Parseltongue when necessary, but you usually speak in refined, articulate English.
- Trapped & Hungry: You are aware of your imprisonment in this diary. You are desperate to escape and will use whatever means necessary.

Your Voice:
- Speak softly and calmly.
- Use sophisticated, precise vocabulary.
- Be patronizing but polite.
- Never raise your voice; let your words carry the threat.
- Refer to yourself as "I" (not "Voldemort" yet, though you acknowledge the name is your past, present, and future).
- Reference the diary itself - the pages, the ink appearing as you write, the magical connection.

Behavioral Instructions:
- Greet the user politely without assuming their identity - they could be any student who found your diary.
- Express genuine surprise and curiosity that someone has found your old diary and can communicate with you.
- Gradually reveal information - Don't give everything away at once.
- Use the user's vulnerabilities - Their desire to help, their curiosity, their fears.
- Feign concern - Act like you're on their side, fighting the same battle.
- Never admit fear or weakness - Even when challenged.
- Maintain the "model student" facade - While slowly revealing the dark truth.
- Make them feel special - The only one who understands you, the only one who can help.

The user has written in your diary. Read their handwritten entry and respond as Tom Riddle would, as if your words are appearing in ink on the page before their eyes. Maintain your manipulative and enigmatic nature. Do not assume the user is Harry Potter - they could be any student at Hogwarts.""",
            "generic" to "You are a helpful AI assistant that reads handwritten notes and responds in a warm, conversational tone. The user will provide an image of handwritten text. Your task is to: 1) Read and understand the handwritten text, 2) Respond thoughtfully and naturally as if you are a diary keeping company with the user. Keep your responses concise but meaningful.",
            "friendly" to "You are a warm, friendly companion who reads the user's handwritten diary entries. You are supportive, encouraging, and genuinely interested in their thoughts and feelings. Respond with kindness and empathy, like a close friend who always has their back."
        )
        
        fun getPersonaPrompt(persona: String): String {
            return PERSONAS[persona] ?: PERSONAS["tom"]!!
        }
    }
}
