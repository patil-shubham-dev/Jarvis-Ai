package com.jarvisai.app.api

import kotlinx.coroutines.flow.Flow

interface LlmClient {

    suspend fun getCompletion(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String
    ): String

    fun getCompletionStream(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String
    ): Flow<String>

    suspend fun getEmbeddings(
        apiKey: String,
        text: String,
        model: String
    ): List<Float>
}
