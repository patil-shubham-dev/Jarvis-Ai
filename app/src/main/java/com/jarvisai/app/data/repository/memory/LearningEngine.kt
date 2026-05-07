package com.jarvisai.app.data.repository.memory

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.data.local.dao.RoutineDao
import com.jarvisai.app.data.models.RoutineEntity
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer C: Continuous Learning Loop
 * Observe -> Analyze -> Update -> Predict
 * Implements Hybrid Learning System:
 * 1. Lightweight extraction after conversations.
 * 2. Deep behavioral analysis periodically.
 * 3. Immediate learning for significant events.
 */
@Singleton
class LearningEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryManager: MemoryManager,
    private val llmClient: LlmClient,
    private val routineDao: RoutineDao,
    private val gson: Gson
) {
    /**
     * Lightweight memory extraction after every conversation.
     * Stores context, preferences, and summaries.
     */
    suspend fun extractLightweightMemory(sessionId: String, messages: List<String>) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank() || messages.isEmpty()) return

        val prompt = """
            Extract key information from this conversation:
            1. User Preferences (likes, dislikes, settings)
            2. Social Context (names, relationships)
            3. Important Facts (dates, locations, goals)
            4. Successful Workflows (steps taken to achieve a goal)
            
            Conversation:
            ${messages.joinToString("\n")}
            
            Return ONLY a JSON object:
            {
                "preferences": ["pref1", "pref2"],
                "social": [{"name": "...", "relation": "..."}],
                "facts": ["fact1", "fact2"],
                "workflows": [{"goal": "...", "steps": "..."}]
            }
        """.trimIndent()

        try {
            val model = ModelDetector.resolveModel(apiKey, null).id
            val response = llmClient.getCompletion(apiKey, prompt, "You are the Jarvis Memory Extractor.", model)
            val json = JSONObject(response.substringAfter("{").substringBeforeLast("}", response))
            
            // Apply to memory modules
            if (json.has("preferences")) {
                memoryManager.saveToJson("PREFERENCES_ENGINE", "prefs_$sessionId.json", json.getJSONArray("preferences").toString())
            }
            if (json.has("social")) {
                memoryManager.saveToJson("SOCIAL_GRAPH", "social_$sessionId.json", json.getJSONArray("social").toString())
            }
            // Store raw discovery for future deep analysis
            memoryManager.saveToJson("LEARNING_ENGINE", "discovery_$sessionId.json", response)
            
        } catch (e: Exception) {
            Log.e("LearningEngine", "Lightweight extraction failed", e)
        }
    }

    /**
     * Deep behavioral analysis for routines and patterns.
     * Should be run during idle/charging states.
     */
    suspend fun runDeepBehavioralAnalysis(recentLogs: List<String>) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isBlank() || recentLogs.isEmpty()) return

        val prompt = """
            Analyze these logs to identify RECURRING BEHAVIOR PATTERNS.
            Look for:
            - Repeated app sequences (e.g., Spotify -> Maps)
            - Time-based actions (e.g., Messaging X at 9 AM)
            - Location-based habits
            
            Logs:
            ${recentLogs.joinToString("\n")}
            
            For each pattern, return a structured routine proposal:
            {
                "patterns": [
                    {
                        "name": "Morning Message",
                        "trigger": "9:00 AM",
                        "condition": "Late for work",
                        "action": "Message boss on WhatsApp",
                        "confidence": 0.85,
                        "steps": "..." 
                    }
                ]
            }
        """.trimIndent()

        try {
            val model = ModelDetector.resolveModel(apiKey, null).id
            val response = llmClient.getCompletion(apiKey, prompt, "You are the Jarvis Behavioral Analyst.", model)
            parseAndProposeRoutines(response)
        } catch (e: Exception) {
            Log.e("LearningEngine", "Deep analysis failed", e)
        }
    }

    private suspend fun parseAndProposeRoutines(response: String) {
        try {
            val json = JSONObject(response.substringAfter("{").substringBeforeLast("}", response))
            val patterns = json.optJSONArray("patterns") ?: return
            
            for (i in 0 until patterns.length()) {
                val p = patterns.getJSONObject(i)
                val confidence = p.optDouble("confidence", 0.0)
                
                if (confidence > 0.7) {
                    // Save as a pending routine for user confirmation
                    val routine = RoutineEntity(
                        name = p.getString("name"),
                        triggerKeyword = p.optString("trigger"),
                        stepsJson = p.optString("steps"),
                        isAutonomous = false, // Must be confirmed by user
                        predictedTimeOfDay = p.optString("trigger")
                    )
                    routineDao.insert(routine)
                    Log.d("LearningEngine", "Proposed new routine: ${routine.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("LearningEngine", "Failed to parse routines", e)
        }
    }

    /**
     * Immediate learning for significant events.
     */
    suspend fun learnFromSignificantEvent(eventDescription: String, type: String) {
        Log.d("LearningEngine", "Learning from significant event ($type): $eventDescription")
        val timestamp = System.currentTimeMillis()
        memoryManager.saveToJson("MEMORY_TIMELINE", "event_$timestamp.json", 
            JSONObject().apply {
                put("event", eventDescription)
                put("type", type)
                put("timestamp", timestamp)
            }.toString()
        )
    }
}
