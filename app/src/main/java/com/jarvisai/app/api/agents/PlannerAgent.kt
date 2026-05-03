package com.jarvisai.app.api.agents

import com.jarvisai.app.core.action.ActionEngine
import com.jarvisai.app.data.repository.memory.MemoryManager
import com.jarvisai.app.service.JarvisAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PlannerAgent @Inject constructor(
    private val actionEngine: ActionEngine,
    private val memoryManager: MemoryManager
) {

    /**
     * Returns OpenAI-compatible tool JSON definitions for system actions.
     */
    fun getToolDefinitions(): JSONArray {
        return JSONArray().apply {
            put(createFunction("open_app", "Opens a specific Android application by name.", mapOf("appName" to "string")))
            put(createFunction("send_whatsapp", "Sends a message to a person via WhatsApp.", mapOf("contact" to "string", "message" to "string")))
            put(createFunction("search_web", "Searches the web for information using Google.", mapOf("query" to "string")))
            put(createFunction("read_screen", "Reads the text content of the current foreground screen.", emptyMap()))
            put(createFunction("click_on_text", "Performs a click action on a UI element with specific text.", mapOf("text" to "string")))
            put(createFunction("update_memory", "Updates a specific local memory module.", mapOf("module" to "string", "file" to "string", "content" to "object")))
        }
    }

    private fun createFunction(name: String, desc: String, params: Map<String, String>): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", desc)
                if (params.isNotEmpty()) {
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        params.forEach { (k, v) -> props.put(k, JSONObject().put("type", v)) }
                        put("properties", props)
                    })
                }
            })
        }
    }

    suspend fun processIntent(llmResponseOrUserCommand: String): String? {
        try {
            val json = JSONObject(llmResponseOrUserCommand)
            
            if (json.has("tool_calls")) {
                val calls = json.getJSONArray("tool_calls")
                val results = JSONArray()
                
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val function = call.getJSONObject("function")
                    val name = function.getString("name")
                    val args = JSONObject(function.optString("arguments", "{}"))
                    val callId = call.optString("id", "call_$i")
                    
                    val result = when (name) {
                        "open_app" -> actionEngine.execute(ActionEngine.IntentParsed(ActionEngine.ActionType.OPEN_APP, target = args.optString("appName")))
                        "send_whatsapp" -> actionEngine.execute(ActionEngine.IntentParsed(ActionEngine.ActionType.SEND_MESSAGE, target = args.optString("contact"), content = args.optString("message")))
                        "search_web" -> actionEngine.execute(ActionEngine.IntentParsed(ActionEngine.ActionType.SEARCH_WEB, content = args.optString("query")))
                        "read_screen" -> JarvisAccessibilityService.instance?.getScreenContent() ?: "Accessibility service not active."
                        "click_on_text" -> JarvisAccessibilityService.instance?.performActionClick(args.optString("text")) ?: "Accessibility service not active."
                        "update_memory" -> {
                            val map = mutableMapOf<String, Any>()
                            val content = args.optJSONObject("content")
                            content?.keys()?.forEach { key -> map[key] = content.get(key) }
                            memoryManager.saveToJson(args.getString("module"), args.getString("file"), map)
                            true
                        }
                        else -> "Unknown function"
                    }
                    
                    results.put(JSONObject().apply {
                        put("tool_call_id", callId)
                        put("role", "tool")
                        put("name", name)
                        put("content", result.toString())
                    })
                }
                return results.toString()
            }
        } catch (e: Exception) {
            // Not a JSON command or tool call
        }
        return null
    }
}
