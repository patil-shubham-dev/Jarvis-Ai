package com.jarvisai.app.api.agents

import com.jarvisai.app.data.repository.memory.MemoryManager
import com.jarvisai.app.data.repository.memory.VectorMemoryStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer B: Multi-Agent Orchestration - Memory Agent
 * Reads/Writes to specific JARVIS JSON/Vector modules.
 */
@Singleton
class MemoryAgent @Inject constructor(
    private val memoryManager: MemoryManager,
    private val vectorMemoryStore: VectorMemoryStore
) {
    /**
     * Recalls relevant context snippets and summarized system state.
     */
    suspend fun recallSemanticContext(query: String): String {
        val snippets = vectorMemoryStore.search(query, limit = 5)
        if (snippets.isEmpty()) return "No specific long-term memory found for: $query"
        
        return buildString {
            append("--- RECALLED MEMORIES ---\n")
            snippets.forEach { append("- $it\n") }
        }
    }

    /**
     * Persists new interaction or observation into the memory system.
     */
    suspend fun memorize(source: String, text: String, module: String) {
        // 1. Store in Vector DB for semantic recall
        vectorMemoryStore.store(text, module)
        
        // 2. (Continuous Learning) Store in the correct module directory
        val targetModule = module.ifBlank { "SYSTEM_LOGS" }
        memoryManager.saveToJson(targetModule, "observation_${System.currentTimeMillis()}.json", mapOf(
            "source" to source,
            "text" to text,
            "module" to module,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * Gets user-specific identity or patterns from JSON files.
     */
    fun getStructuredContext(module: String, fileName: String): String? {
        return memoryManager.readFromJson(module, fileName)
    }
}
