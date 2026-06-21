package com.jarvisai.app.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

@Singleton
class OpenAILlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LlmClient {

    private fun extractMessageContent(message: JSONObject): String {
        // If it has tool calls, return them as a JSON string for the PlannerAgent
        if (message.has("tool_calls")) {
            return JSONObject().apply {
                put("tool_calls", message.getJSONArray("tool_calls"))
            }.toString()
        }

        // Handle Anthropic tool_use blocks in content
        val contentValue = message.opt("content")
        if (contentValue is JSONArray) {
            val toolCalls = JSONArray()
            var hasTools = false
            for (i in 0 until contentValue.length()) {
                val block = contentValue.optJSONObject(i) ?: continue
                if (block.optString("type") == "tool_use") {
                    hasTools = true
                    toolCalls.put(JSONObject().apply {
                        put("id", block.optString("id"))
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", block.optString("name"))
                            put("arguments", block.optJSONObject("input")?.toString() ?: "{}")
                        })
                    })
                }
            }
            if (hasTools) {
                return JSONObject().apply { put("tool_calls", toolCalls) }.toString()
            }
        }

        return when (contentValue) {
            is String -> contentValue
            is JSONArray -> {
                buildString {
                    for (i in 0 until contentValue.length()) {
                        when (val part = contentValue.opt(i)) {
                            is String -> append(part)
                            is JSONObject -> {
                                val text = part.optString("text")
                                if (text.isNotBlank()) append(text)
                                else if (part.optString("type") == "text") append(part.optString("value"))
                            }
                        }
                    }
                }
            }
            is JSONObject -> contentValue.optString("text")
            else -> ""
        }.trim()
    }

    override fun getCompletionStream(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String,
        toolsJson: JSONArray?,
        customBaseUrl: String?,
        base64Image: String?
    ): Flow<String> = callbackFlow {
        val provider = ModelDetector.detect(apiKey, customBaseUrl)
        val baseUrl = provider.effectiveBaseUrl.trim().trimEnd('/')
        
        val url = if (provider.provider == ModelDetector.Provider.ANTHROPIC) "$baseUrl/messages" else "$baseUrl/chat/completions"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", if (provider.provider == ModelDetector.Provider.NVIDIA) 512 else 1024)
            put("temperature", 0.1) // Lower temperature for more stable tool calls
            put("stream", true)
            
            val msgs = JSONArray().apply {
                if (provider.provider != ModelDetector.Provider.ANTHROPIC) {
                    put(JSONObject().put("role", "system").put("content", systemContext))
                }
                put(JSONObject().apply {
                    put("role", "user")
                    if (base64Image != null) {
                        put("content", JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", prompt))
                            if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                                put(JSONObject().put("type", "image").put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", base64Image)
                                }))
                            } else {
                                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image")))
                            }
                        })
                    } else {
                        put("content", prompt)
                    }
                })
            }
            put("messages", msgs)
            if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                put("system", systemContext)
            }
            
            if (toolsJson != null && toolsJson.length() > 0) {
                put("tools", toolsJson)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header(provider.authHeaderName, provider.authHeaderValue(apiKey))
            .apply { provider.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") { close(); return }
                try {
                    val json = JSONObject(data)
                    if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                        if (json.optString("type") == "content_block_delta") {
                            val text = json.optJSONObject("delta")?.optString("text")
                            if (!text.isNullOrEmpty()) trySend(text)
                        }
                    } else {
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val content = choices.getJSONObject(0).optJSONObject("delta")?.optString("content")
                            if (!content.isNullOrEmpty()) trySend(content)
                        }
                    }
                } catch (e: Exception) { Log.e("OpenAILlmClient", "Stream error", e) }
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                close(Exception("Stream failed: ${t?.message}"))
            }
            override fun onClosed(eventSource: EventSource) { close() }
        }
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    override suspend fun getChatCompletion(
        apiKey: String,
        messages: List<Message>,
        systemContext: String,
        model: String,
        toolsJson: JSONArray?,
        customBaseUrl: String?
    ): String {
        val provider = ModelDetector.detect(apiKey, customBaseUrl)
        val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
        val url = if (provider.provider == ModelDetector.Provider.ANTHROPIC) "$baseUrl/messages" else "$baseUrl/chat/completions"
        
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", if (provider.provider == ModelDetector.Provider.NVIDIA) 512 else 1024)
            put("temperature", 0.0) 
            
            if (toolsJson != null && toolsJson.length() > 0) {
                if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                    val anthropicTools = JSONArray()
                    for (i in 0 until toolsJson.length()) {
                        val tool = toolsJson.getJSONObject(i).getJSONObject("function")
                        anthropicTools.put(JSONObject().apply {
                            put("name", tool.getString("name"))
                            put("description", tool.getString("description"))
                            put("input_schema", tool.optJSONObject("parameters") ?: JSONObject().put("type", "object").put("properties", JSONObject()))
                        })
                    }
                    put("tools", anthropicTools)
                } else {
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                }
            }

            val messagesArray = JSONArray()
            if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                put("system", systemContext)
                messages.forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", msg.role)
                        if (msg.imageUrl != null) {
                            put("content", JSONArray().apply {
                                put(JSONObject().put("type", "text").put("text", msg.content))
                                put(JSONObject().put("type", "image").put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", msg.imageUrl)
                                }))
                            })
                        } else {
                            put("content", msg.content)
                        }
                    })
                }
            } else {
                messagesArray.put(JSONObject().put("role", "system").put("content", systemContext))
                messages.forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", msg.role)
                        if (msg.imageUrl != null) {
                            put("content", JSONArray().apply {
                                put(JSONObject().put("type", "text").put("text", msg.content))
                                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${msg.imageUrl}")))
                            })
                        } else {
                            put("content", msg.content)
                        }
                    })
                }
            }
            put("messages", messagesArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header(provider.authHeaderName, provider.authHeaderValue(apiKey))
            .apply { provider.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("API Error: $body")
            
            val json = JSONObject(body)
            val message = if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                val content = json.getJSONArray("content")
                JSONObject().put("content", content)
            } else {
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            }

            extractMessageContent(message)
        }
    }

    override suspend fun getCompletion(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String,
        toolsJson: JSONArray?,
        customBaseUrl: String?,
        base64Image: String?
    ): String {
        val messages = listOf(Message(role = "user", content = prompt, imageUrl = base64Image))
        return getChatCompletion(apiKey, messages, systemContext, model, toolsJson, customBaseUrl)
    }


    override suspend fun getEmbeddings(apiKey: String, text: String, model: String): List<Float> {
        val provider = ModelDetector.detect(apiKey)
        val url = "${provider.effectiveBaseUrl.trimEnd('/')}/embeddings"
        val bodyJson = JSONObject().apply { put("model", model); put("input", text) }
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header(provider.authHeaderName, provider.authHeaderValue(apiKey))
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Embedding Error: $body")
            val data = JSONObject(body).getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
            List(data.length()) { data.getDouble(it).toFloat() }
        }
    }
}
