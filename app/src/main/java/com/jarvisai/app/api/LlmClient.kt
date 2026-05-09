package com.jarvisai.app.api

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

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
