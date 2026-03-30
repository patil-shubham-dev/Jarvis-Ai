package com.jarvisai.app.core.memory

import android.content.Context
import com.jarvisai.app.core.ai.LlmClient
import com.jarvisai.app.data.local.dao.MemoryDao
import com.jarvisai.app.data.local.entity.MemorySnippetEntity
import com.jarvisai.app.util.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Layer A: Vector Memory (Semantic Recall)
 * Converts text into embeddings and performs cosine similarity for context retrieval.
 */
@Singleton
class VectorMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val llmClient: LlmClient
) {
    /**
     * Stores a memory snippet with its vector embedding.
     */
    suspend fun store(text: String, module: String) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return

        try {
            val embedding = llmClient.getEmbeddings(apiKey, text)
            memoryDao.insertSnippet(MemorySnippetEntity(
                text = text,
                embedding = embedding,
                metadata = module
            ))
        } catch (e: Exception) {
            // Log error silently
        }
    }

    /**
     * Retrieves the 5 most relevant context snippets for a query.
     */
    suspend fun search(query: String, limit: Int = 5): List<String> {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return emptyList()

        return try {
            val queryEmbedding = llmClient.getEmbeddings(apiKey, query)
            val allSnippets = memoryDao.getAllSnippets()

            allSnippets
                .map { it to cosineSimilarity(queryEmbedding, it.embedding) }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first.text }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Standard Cosine Similarity calculation.
     */
    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }
}
