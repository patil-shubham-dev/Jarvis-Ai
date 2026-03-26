package com.jarvis.assistant.core.api

import com.jarvis.assistant.data.models.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeApi {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        userMessage: String,
        apiKey: String,
        model: String,
        history: List<Pair<String, String>>,
        memories: List<MemoryEntity>
    ): Flow<String> = flow {
        val messages = JSONArray().apply {
            history.takeLast(10).forEach { (role, content) ->
                put(JSONObject().put("role", role).put("content", content))
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val requestBody = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("stream", true)
            .put("system", buildSystemPrompt(memories))
            .put("messages", messages)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody)
            .build()

        withContext(Dispatchers.IO) {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.body?.string()}")
            }
            val source = response.body?.source() ?: throw Exception("Empty response body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)
                    if (json.optString("type") == "content_block_delta") {
                        val chunk = json.optJSONObject("delta")?.optString("text") ?: continue
                        if (chunk.isNotEmpty()) emit(chunk)
                    }
                } catch (_: Exception) { /* skip malformed SSE lines */ }
            }
        }
    }

    private fun buildSystemPrompt(memories: List<MemoryEntity>): String {
        val memoryBlock = if (memories.isEmpty()) "" else buildString {
            append("\n\n━━━ KNOWN USER CONTEXT ━━━\n")
            memories.groupBy { it.module }.forEach { (module, items) ->
                append("[$module]\n")
                items.forEach { append("  • ${it.key}: ${it.value}\n") }
            }
            append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        }

        return """
You are JARVIS — a warm, intelligent personal AI assistant running on Android.

Personality: Friendly, concise, slightly witty. Like a genius best friend.
Language: Respond naturally in Hindi, English, or Hinglish — match the user.
TTS awareness: Keep responses short (2–3 sentences) since they may be spoken aloud.
$memoryBlock

━━━ PHONE COMMAND FORMAT ━━━
When the user wants to perform a phone action, reply ONLY with this JSON (no extra text):

{"action":"ACTION","params":{...},"speech":"what to say aloud","sensitive":false}

Actions:
  CALL            params: {"contact":"name or number"}
  SEND_WHATSAPP   params: {"contact":"name","message":"text"}       sensitive=true
  SEND_SMS        params: {"contact":"name or number","message":"text"}  sensitive=true
  SET_ALARM       params: {"hour":7,"minute":30,"label":"label"}
  OPEN_APP        params: {"name":"appName"}
  WIFI            params: {"state":"on|off"}
  BLUETOOTH       params: {"state":"on|off"}
  VOLUME          params: {"level":70}
  BRIGHTNESS      params: {"level":80}
  TAKE_PHOTO      params: {}
  PLAY_MUSIC      params: {"query":"song or artist"}
  NAVIGATE        params: {"destination":"place"}
  GOOGLE_SEARCH   params: {"query":"search term"}
  MULTI_STEP      params: {"steps":[{"action":"...","params":{...}},...]}

━━━ MEMORY EXTRACTION ━━━
After every plain-text response, if you learned something durable about the user,
append exactly this at the end (invisible to the user):

<<<MEMORY>>>
MODULE|key|value
<<<END>>>

Modules: IDENTITY | SOCIAL | BEHAVIOR | PREFERENCES | LIFE_OPS | KNOWLEDGE
Only record genuinely useful, lasting facts — not transient conversation details.
""".trimIndent()
    }
}
