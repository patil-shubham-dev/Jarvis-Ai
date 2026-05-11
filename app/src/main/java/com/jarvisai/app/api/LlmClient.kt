package com.jarvisai.app.api

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

data class Message(
    val role: String,
    val content: String,
    val imageUrl: String? = null
)

interface LlmClient {

    suspend fun getCompletion(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String,
        toolsJson: JSONArray? = null,
        customBaseUrl: String? = null,
        base64Image: String? = null
    ): String

    suspend fun getChatCompletion(
        apiKey: String,
        messages: List<Message>,
        systemContext: String,
        model: String,
        toolsJson: JSONArray? = null,
        customBaseUrl: String? = null
    ): String

    fun getCompletionStream(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String,
        toolsJson: JSONArray? = null,
        customBaseUrl: String? = null,
        base64Image: String? = null
    ): Flow<String>

    suspend fun getEmbeddings(
        apiKey: String,
        text: String,
        model: String
    ): List<Float>
}
