package com.jarvisai.app.data.repository.memory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Long-term Vector-like memory store for Sentinel V3.
 * Uses embeddings for semantic search of previous workflows and context.
 */
@Singleton
class SemanticMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val gson: Gson
) {
    data class MemoryEntry(
        val id: String,
        val text: String,
        val vector: List<Float>,
        val metadata: Map<String, String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val memoryFile = File(context.filesDir, "semantic_memory.json")
    private var entries: MutableList<MemoryEntry> = mutableListOf()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        if (memoryFile.exists()) {
            val json = memoryFile.readText()
            val type = object : TypeToken<MutableList<MemoryEntry>>() {}.type
            entries = gson.fromJson(json, type) ?: mutableListOf()
        }
    }

    private fun saveMemories() {
        val json = gson.toJson(entries)
        memoryFile.writeText(json)
    }

    suspend fun addMemory(text: String, metadata: Map<String, String> = emptyMap()) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return

        val vector = try {
            llmClient.getEmbeddings(apiKey, text, "text-embedding-3-small")
        } catch (e: Exception) {
            android.util.Log.e("SemanticMemory", "Failed to get embedding: ${e.message}")
            return
        }
        val entry = MemoryEntry(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            vector = vector,
            metadata = metadata
        )
        
        withContext(Dispatchers.IO) {
            entries.add(entry)
            saveMemories()
        }
    }

    suspend fun search(query: String, limit: Int = 3): List<MemoryEntry> {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return emptyList()

        val queryVector = try {
            llmClient.getEmbeddings(apiKey, query, "text-embedding-3-small")
        } catch (e: Exception) {
            android.util.Log.e("SemanticMemory", "Search failed: ${e.message}")
            return emptyList()
        }
        
        return entries.map { entry ->
            entry to cosineSimilarity(queryVector, entry.vector)
        }.sortedByDescending { it.second }
         .take(limit)
         .map { it.first }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    fun clear() {
        entries.clear()
        saveMemories()
    }

    /**
     * Improvement 7: Conversation Memory / Persistent Profile.
     * Saves a specific preference to the vector store.
     */
    suspend fun savePreference(key: String, value: String) {
        addMemory("Preference: User prefers $key to be $value", mapOf("type" to "preference", "key" to key))
    }

    /**
     * Retrieves all memories tagged as preferences to build a user profile.
     */
    fun getPreferences(): List<String> {
        return entries.filter { it.metadata["type"] == "preference" }
                      .map { it.text }
    }
}
