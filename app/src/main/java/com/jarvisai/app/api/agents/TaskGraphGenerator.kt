package com.jarvisai.app.api.agents

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.utils.SecurePrefs
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a structured execution graph for complex autonomous tasks.
 */
@Singleton
class TaskGraphGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val gson: Gson
) {

    suspend fun generateGraph(intent: String, deviceState: String): ExecutionGraph? {
        val apiKey = SecurePrefs.getApiKey(context)
        val systemPrompt = """
            You are a Task Graph Generator for JARVIS.
            Decompose the user's intent into a structured JSON execution graph.
            Each step must use one of the available tools:
            - send_whatsapp(recipient, message)
            - play_music(query)
            - open_app(package_name)
            - see_screen()
            - click_element(query)
            - type_text(query, text)
            
            Return ONLY a JSON object:
            {
              "goal": "The high-level goal",
              "steps": [
                { "id": "1", "description": "Open WhatsApp", "toolName": "open_app", "params": { "package_name": "com.whatsapp" } },
                ...
              ]
            }
        """.trimIndent()

        val response = llmClient.getCompletion(
            apiKey = apiKey,
            prompt = "Intent: $intent\nDevice State: $deviceState",
            systemContext = systemPrompt,
            model = SecurePrefs.getSelectedModel(context) ?: "gpt-4o"
        )

        return try {
            val type = object : TypeToken<ExecutionGraph>() {}.type
            gson.fromJson(response, type)
        } catch (e: Exception) {
            null
        }
    }
}
