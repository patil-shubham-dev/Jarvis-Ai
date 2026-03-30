package com.jarvisai.app.api

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
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

@Singleton
class OpenAILlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient
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
                if (data == "[DONE]") {
                    close()
                    return
                }
                try {
                    val json = JSONObject(data)
                    // Early exit if the stream data itself is an error
                    if (json.has("error")) {
                        val error = json.getJSONObject("error")
                        close(Exception(error.optString("message", "API Error")))
                        return
                    }

                    val content = if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                        // Anthropic delta content
                        json.optJSONObject("delta")?.optString("text")
                    } else {
                        // OpenAI / OpenRouter / Groq / Mistral
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            delta?.optString("content") ?: choices.getJSONObject(0).optString("text", "")
                        } else null
                    }
                    if (!content.isNullOrEmpty()) {
                        trySend(content)
                    }
                } catch (e: Exception) {
                    // Metadata signals
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val errorMsg = response?.let {
                    try {
                        val body = it.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.has("error")) {
                            val err = json.getJSONObject("error")
                            err.optString("message", "Status ${it.code}")
                        } else "Status ${it.code}"
                    } catch (e: Exception) {
                        "Status ${it.code}"
                    }
                } ?: t?.localizedMessage ?: "Streaming failure"
                close(Exception(errorMsg))
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
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key is missing. Please add it in Settings.")
        }

        // Detect the provider to get dynamic base URL and headers
        val provider = ModelDetector.detect(apiKey)
        val baseUrl = provider.baseUrl.trimEnd('/')

        // Build an OpenAI-compatible request for all providers
        // Anthropic has a different endpoint format so handle specially
        val (url, bodyJson) = if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", prompt))
            }
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1024)
                put("system", systemContext)
                put("messages", messages)
            }
            Pair("$baseUrl/messages", body)
        } else {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemContext))
                put(JSONObject().put("role", "user").put("content", prompt))
            }
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1024)
                put("temperature", 0.7)
                put("messages", messages)
            }
            Pair("$baseUrl/chat/completions", body)
        }

        val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())

        // Build request with dynamic auth header + any extra headers (e.g. HTTP-Referer for OpenRouter)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header(provider.authHeaderName, provider.authHeaderValue(apiKey))

        provider.extraHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.build()

        Log.d("LlmClient", "Sending request to: $url | Provider: ${provider.displayName}")

        val client = okHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("LlmClient", "Error ${response.code}: $responseBody")
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody
                } catch (e: Exception) { responseBody }
                throw Exception("API Error (${response.code}): $errorMsg")
            }

            try {
                val json = JSONObject(responseBody)
                if (provider.provider == ModelDetector.Provider.ANTHROPIC) {
                    json.getJSONArray("content").getJSONObject(0).getString("text").trim()
                } else {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim()
                }
            } catch (e: Exception) {
                throw Exception("Failed to parse response: ${e.message}\nRaw: $responseBody")
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
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) throw Exception("Embedding Error: $responseBody")

            val embeddings = mutableListOf<Float>()
            try {
                val data = JSONObject(responseBody).getJSONArray("data")
                    .getJSONObject(0).getJSONArray("embedding")
                for (i in 0 until data.length()) {
                    embeddings.add(data.getDouble(i).toFloat())
                }
            } catch (e: Exception) {
                throw Exception("Failed to parse embedding: $responseBody")
            }
            embeddings
        }
    }
}
