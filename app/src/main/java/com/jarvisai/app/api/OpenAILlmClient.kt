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
import com.jarvisai.app.api.agents.PlannerAgent

@Singleton
class OpenAILlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val plannerAgent: PlannerAgent
) : LlmClient {

    override fun getCompletionStream(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String
    ): Flow<String> = callbackFlow {
        val provider = ModelDetector.detect(apiKey)
        val baseUrl = provider.baseUrl.trimEnd('/')
        
        val url = if (provider.provider == ModelDetector.Provider.ANTHROPIC) "$baseUrl/messages" else "$baseUrl/chat/completions"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("stream", true)
            if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                put("max_tokens", 1024)
                put("system", systemContext)
                put("messages", JSONArray().apply { put(JSONObject().put("role", "user").put("content", prompt)) })
            } else {
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemContext))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
                // Add tools for non-Anthropic providers
                put("tools", plannerAgent.getToolDefinitions())
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header(provider.authHeaderName, provider.authHeaderValue(apiKey))
            .apply { provider.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        val listener = object : EventSourceListener() {
            private var toolCallBuilder = JSONObject().apply { put("tool_calls", JSONArray()) }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    if (toolCallBuilder.getJSONArray("tool_calls").length() > 0) {
                        trySend(toolCallBuilder.toString())
                    }
                    close()
                    return
                }
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        
                        // Handle Content
                        val content = delta?.optString("content")
                        if (!content.isNullOrEmpty()) {
                            trySend(content)
                        }

                        // Handle Tool Calls (Accumulate)
                        val toolCalls = delta?.optJSONArray("tool_calls")
                        if (toolCalls != null) {
                            for (i in 0 until toolCalls.length()) {
                                val call = toolCalls.getJSONObject(i)
                                val index = call.optInt("index", 0)
                                val existingCalls = toolCallBuilder.getJSONArray("tool_calls")
                                
                                if (index >= existingCalls.length()) {
                                    existingCalls.put(call)
                                } else {
                                    val existing = existingCalls.getJSONObject(index)
                                    val func = call.optJSONObject("function")
                                    if (func != null) {
                                        val existingFunc = existing.getJSONObject("function")
                                        existingFunc.put("arguments", existingFunc.optString("arguments", "") + func.optString("arguments", ""))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OpenAILlmClient", "Stream parse error", e)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    override suspend fun getCompletion(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String
    ): String {
        val provider = ModelDetector.detect(apiKey)
        val baseUrl = provider.baseUrl.trimEnd('/')
        
        val url = if (provider.provider == ModelDetector.Provider.ANTHROPIC) "$baseUrl/messages" else "$baseUrl/chat/completions"
        val bodyJson = JSONObject().apply {
            put("model", model)
            if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                put("max_tokens", 1024)
                put("system", systemContext)
                put("messages", JSONArray().apply { put(JSONObject().put("role", "user").put("content", prompt)) })
            } else {
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemContext))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
                put("tools", plannerAgent.getToolDefinitions())
                put("tool_choice", "auto")
            }
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
            val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            
            if (message.has("tool_calls")) {
                message.toString() // Return the whole message JSON if it contains tool calls
            } else {
                message.getString("content")
            }
        }
    }

    override suspend fun getEmbeddings(apiKey: String, text: String, model: String): List<Float> {
        val provider = ModelDetector.detect(apiKey)
        val url = "${provider.baseUrl.trimEnd('/')}/embeddings"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("input", text)
        }
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
