package com.jarvisai.app.data.repository.memory

import android.content.Context
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.data.local.dao.MemoryDao
import com.jarvisai.app.data.models.MemorySnippetEntity
import com.jarvisai.app.utils.SecurePrefs
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
     * Stores a memory snippet with its vector embedding and metadata.
     */
    suspend fun store(
        text: String,
        module: String,
        type: String = "context",
        importance: Float = 0.5f,
        summary: String = ""
    ) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return

        try {
            val embedding = llmClient.getEmbeddings(apiKey, text, "text-embedding-3-small")
            
            // Deduplication check (limited to most recent 200 to avoid O(n) full scan)
            val recentSnippets = memoryDao.getRecentSnippets(200)
            val mostSimilar = recentSnippets
                .map { it to cosineSimilarity(embedding, it.embedding) }
                .maxByOrNull { it.second }

            if (mostSimilar != null && mostSimilar.second > 0.9f) {
                val existing = mostSimilar.first
                memoryDao.insertSnippet(existing.copy(
                    text = text,
                    embedding = embedding,
                    importance = importance,
                    summary = summary,
                    timestamp = System.currentTimeMillis()
                ))
                return
            }

            memoryDao.insertSnippet(MemorySnippetEntity(
                text = text,
                embedding = embedding,
                type = type,
                importance = importance,
                module = module,
                summary = summary
            ))
        } catch (e: Exception) {
            android.util.Log.e("VectorMemoryStore", "Failed to store memory: ${e.message}")
        }
    }

    /**
     * Retrieves the 5 most relevant context snippets for a query.
     */
    suspend fun search(query: String, limit: Int = 5): List<String> {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return emptyList()

        return try {
            val queryEmbedding = llmClient.getEmbeddings(apiKey, query, "text-embedding-3-small")
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
