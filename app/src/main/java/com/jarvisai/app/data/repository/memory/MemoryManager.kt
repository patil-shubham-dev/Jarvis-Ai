package com.jarvisai.app.core.memory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the JARVIS CORE MEMORY SYSTEM (16-Module Hierarchy).
 * Every user interaction is categorized and routed to these nodes.
 */
@Singleton
class MemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val memoryBaseDir = File(context.filesDir, "JARVIS").apply { mkdirs() }

    // 16-Module Folder Structure
    private val modules = listOf(
        "CORE_IDENTITY", "SOCIAL_GRAPH", "BEHAVIORAL_INTELLIGENCE", "KNOWLEDGE_BASE",
        "MEMORY_TIMELINE", "PREFERENCES_ENGINE", "LIFE_OPERATIONS", "COMMUNICATIONS",
        "DIGITAL_FOOTPRINT", "DECISION_ENGINE", "HEALTH_PROFILE", "FINANCIAL_SYSTEM",
        "SECURITY_VAULT", "LEARNING_ENGINE", "CONTEXT_ENGINE", "SYSTEM_LOGS"
    )

    private val moduleDirs = modules.associateWith { name ->
        File(memoryBaseDir, name).apply { mkdirs() }
    }

    init {
        initializeDefaults()
    }

    private fun initializeDefaults() {
        // Initialize basic structure if not exists
        val coreIdFile = File(moduleDirs["CORE_IDENTITY"], "basic_profile.json")
        if (!coreIdFile.exists()) {
            saveToJson("CORE_IDENTITY", "basic_profile.json", mapOf(
                "name" to "User",
                "role" to "Architect",
                "personality" to "Ambitious, logical, focused"
            ))
        }
    }

    /**
     * Saves generic data to a specific module.
     */
    fun saveToJson(moduleName: String, fileName: String, data: Any) {
        val dir = moduleDirs[moduleName] ?: return
        val file = File(dir, if (fileName.endsWith(".json")) fileName else "$fileName.json")
        file.writeText(gson.toJson(data))
    }

    /**
     * Reads generic data from a specific module.
     */
    fun <T> readFromJson(moduleName: String, fileName: String, typeToken: TypeToken<T>): T? {
        val dir = moduleDirs[moduleName] ?: return null
        val file = File(dir, if (fileName.endsWith(".json")) fileName else "$fileName.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), typeToken.type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets all files in a module for inspection (e.g., for the Dashboard).
     */
    fun getModuleFiles(moduleName: String): List<File> {
        return moduleDirs[moduleName]?.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Specialized: Update a person in Social Graph
     */
    fun updateSocialGraph(personName: String, data: Map<String, Any>) {
        saveToJson("SOCIAL_GRAPH", "${personName.lowercase()}.json", data)
    }

    /**
     * Returns a summarized context for the AI prompt.
     */
    fun getSystemContextSummary(): String {
        return buildString {
            append("JARVIS CORE MEMORY STATE:\n")
            append("- Identity: ${getModuleFiles("CORE_IDENTITY").size} logs\n")
            append("- Social: ${getModuleFiles("SOCIAL_GRAPH").size} contacts\n")
            append("- Intelligence: ${getModuleFiles("BEHAVIORAL_INTELLIGENCE").size} patterns\n")
            append("- Learning: ${getModuleFiles("LEARNING_ENGINE").size} updates\n")
        }
    }
}

