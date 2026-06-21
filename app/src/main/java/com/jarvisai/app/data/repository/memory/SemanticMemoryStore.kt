package com.jarvisai.app.data.repository.memory

import android.content.Context
import android.util.Log
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
 * Long-term Vector-like memory store.
 * Uses embeddings for semantic search with TTL-based pruning and size limits.
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

    companion object {
        private const val TAG = "SemanticMemory"
        private const val MAX_ENTRIES = 500
        private const val TTL_DAYS = 90L
        private const val TTL_MS = TTL_DAYS * 24 * 60 * 60 * 1000
        private const val SEARCH_LIMIT = 10
    }

    private val memoryFile = File(context.filesDir, "semantic_memory.json")
    private var entries: MutableList<MemoryEntry> = mutableListOf()

    init {
        loadMemories()
        pruneOldEntries()
    }

    private fun loadMemories() {
        try {
            if (memoryFile.exists()) {
                val json = memoryFile.readText()
                val type = object : TypeToken<MutableList<MemoryEntry>>() {}.type
                entries = gson.fromJson(json, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load memories: ${e.message}")
            entries = mutableListOf()
        }
    }

    private fun saveMemories() {
        try {
            val json = gson.toJson(entries)
            memoryFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save memories: ${e.message}")
        }
    }

    private fun pruneOldEntries() {
        val cutoff = System.currentTimeMillis() - TTL_MS
        val before = entries.size
        entries = entries.filter { it.timestamp > cutoff }.toMutableList()
        if (entries.size > MAX_ENTRIES) {
            entries = entries.sortedByDescending { it.timestamp }
                .take(MAX_ENTRIES)
                .toMutableList()
        }
        if (before != entries.size) {
            Log.d(TAG, "Pruned ${before - entries.size} old entries. Remaining: ${entries.size}")
            saveMemories()
        }
    }

    suspend fun addMemory(text: String, metadata: Map<String, String> = emptyMap()) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) return

        val vector = try {
            llmClient.getEmbeddings(apiKey, text, "text-embedding-3-small")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get embedding: ${e.message}")
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
            if (entries.size > MAX_ENTRIES) {
                entries = entries.sortedByDescending { it.timestamp }
                    .take(MAX_ENTRIES)
                    .toMutableList()
            }
            saveMemories()
        }
    }

    suspend fun search(query: String, limit: Int = 3): List<MemoryEntry> {
        if (entries.isEmpty()) return emptyList()

        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank()) {
            return entries.sortedByDescending { it.timestamp }
                .take(minOf(limit, entries.size))
        }

        val queryVector = try {
            llmClient.getEmbeddings(apiKey, query, "text-embedding-3-small")
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            return entries.sortedByDescending { it.timestamp }
                .take(minOf(limit, entries.size))
        }

        val results = entries
            .map { entry -> entry to cosineSimilarity(queryVector, entry.vector) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        return results
    }

    fun searchLocal(query: String, limit: Int = 3): List<MemoryEntry> {
        val queryLower = query.lowercase()
        val words = queryLower.split(" ").filter { it.length > 2 }

        if (words.isEmpty()) return entries.take(limit)

        return entries
            .map { entry ->
                val textLower = entry.text.lowercase()
                val score = words.count { textLower.contains(it) }
                entry to score
            }
            .sortedByDescending { it.second }
            .filter { it.second > 0 }
            .take(limit)
            .map { it.first }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom == 0f) 0f else dotProduct / denom
    }

    fun clear() {
        entries.clear()
        saveMemories()
    }

    suspend fun savePreference(key: String, value: String) {
        addMemory("Preference: User prefers $key to be $value", mapOf("type" to "preference", "key" to key))
    }

    fun getPreferences(): List<String> {
        return entries.filter { it.metadata["type"] == "preference" }
            .sortedByDescending { it.timestamp }
            .map { it.text }
    }

    fun size(): Int = entries.size

    fun getRecentEntries(count: Int = 10): List<MemoryEntry> {
        return entries.sortedByDescending { it.timestamp }.take(count)
    }
}
