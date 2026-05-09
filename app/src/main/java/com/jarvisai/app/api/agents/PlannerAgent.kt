package com.jarvisai.app.api.agents

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.api.context.ScreenStateEngine
import com.jarvisai.app.core.action.ActionEngine
import com.jarvisai.app.core.skills.ExecutionTracker
import com.jarvisai.app.core.skills.SkillManager
import com.jarvisai.app.core.security.SafetyEngine
import com.jarvisai.app.utils.SecurePrefs
import com.jarvisai.app.service.JarvisOverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Sentinel V4.1 Recursive Planner.
 * Continuously loops: Observe -> Think -> Act -> Verify.
 * Improvements: Dynamic Skills, Failure Diagnosis, Thought Stream.
 */
@Singleton
class PlannerAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val skillManager: dagger.Lazy<SkillManager>,
    private val executionTracker: ExecutionTracker,
    private val screenStateEngine: ScreenStateEngine,
    private val safetyEngine: SafetyEngine,
    private val actionEngine: ActionEngine,
    private val gson: Gson
) {

    private var isRunning = false

    suspend fun processIntent(userInput: String): String {
        if (isRunning) return "I'm already working on a task."
        isRunning = true
        executionTracker.startGoal(userInput)

        var iteration = 0
        val maxIterations = 15 // Safety cap

        try {
            if (com.jarvisai.app.service.JarvisAccessibilityService.instance == null) {
                isRunning = false
                return "The Accessibility Service is not active. Please enable Jarvis in your device settings."
            }
            JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.THINKING)
            
            while (iteration < maxIterations) {
                iteration++
                Log.d("PlannerAgent", "Iteration $iteration")

                // 1. Observe
                JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.ANALYZING)
                val screenContent = skillManager.get().runSkill("see_screen", emptyMap()).message
                val deviceState = screenStateEngine.getCurrentContextSummary()
                
                // 2. Think
                JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.THINKING)
                val nextStep = decideNextStep(userInput, screenContent, deviceState)
                    ?: break // Goal achieved or stuck

                if (nextStep.toolName == "DONE") {
                    executionTracker.completeGoal()
                    JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.SUCCESS)
                    val finalDesc = nextStep.description ?: "Goal achieved."
                    returnToJarvis(finalDesc)
                    return finalDesc
                }

                // 3. Safety Check
                val safetyCheck = safetyEngine.isActionSafe(nextStep)
                if (!safetyCheck.isSafe) {
                    JarvisOverlayService.instance?.updateStatus("Safety Block: ${safetyCheck.reason}")
                    executionTracker.failGoal("Safety Block: ${safetyCheck.reason}")
                    JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.ERROR)
                    return "Safety Pause: ${safetyCheck.reason}"
                }

                // 4. Act
                if (nextStep.toolName.isNullOrBlank()) {
                    Log.w("PlannerAgent", "Received step with missing toolName")
                    break
                }
                
                JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.EXECUTING)
                executionTracker.addStep(nextStep.description, nextStep.toolName)
                
                // Add to chat as a status update
                skillManager.get().runSkill("communication", mapOf(
                    "type" to "post_status",
                    "text" to "• ${nextStep.description ?: "Executing ${nextStep.toolName}"}"
                ))

                val tool = nextStep.toolName ?: "unknown"
                val params = nextStep.params ?: emptyMap()
                val result = skillManager.get().runSkill(tool, params)
                executionTracker.updateLastStep(result)

                // 5. Verify & Adapt
                if (!result.success) {
                    Log.w("PlannerAgent", "Step failed: ${result.message}. Attempting recovery...")
                    skillManager.get().runSkill("communication", mapOf(
                        "type" to "post_status",
                        "text" to "⚠️ ${result.message}. Retrying..."
                    ))
                    JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.ANALYZING)
                    delay(1000)
                    actionEngine.execute("press back")
                } else {
                    // Update the status to show success
                    skillManager.get().runSkill("communication", mapOf(
                        "type" to "post_status",
                        "text" to "✅ ${nextStep.description}"
                    ))
                }
                
                delay(800)
            }
        } catch (e: Exception) {
            JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.ERROR)
            return "Execution Error: ${e.message}"
        } finally {
            isRunning = false
        }

        return "Task finished."
    }

    private fun updateOverlay(msg: String) {
        JarvisOverlayService.instance?.updateStatus(msg)
    }

    private suspend fun returnToJarvis(summary: String) {
        delay(1500)
        actionEngine.execute("press home")
        delay(500)
        JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.SUCCESS)
    }

    private suspend fun decideNextStep(
        goal: String, 
        screenObservation: String, 
        deviceState: String
    ): TaskStep? {
        val apiKey = SecurePrefs.getApiKey(context)
        val baseUrl = SecurePrefs.getBaseUrl(context)
        
        // Improvement 1: Dynamic Tooling
        val availableTools = skillManager.get().getToolDefinitions()

        val systemPrompt = """
            You are the JARVIS Autonomous Brain (Sentinel V4.1).
            You are a REAL Android Agent, not a simulator.
            
            GOAL: $goal
            DEVICE STATE: $deviceState
            
            SCREEN OBSERVATION (Accessibility + Vision):
            $screenObservation
            
            HISTORY:
            ${executionTracker.getHistorySummary()}
            
            INSTRUCTIONS:
            1. Analyze the screen. Identify the coordinates of elements you need to interact with.
            2. If you need to find a contact, look for the Search icon or search bar.
            3. If you are in the correct app but not on the target screen, navigate (click/swipe).
            4. If the task is completed, return toolName: "DONE" with a concise success summary.
            5. Return ONLY a JSON TaskStep.
            
            AVAILABLE TOOLS:
            $availableTools
            - tap_at(x, y): Click specific coordinates.
            - type_at(content, x, y): Click then type text.
            - swipe_at(x, y, x2, y2): Scroll or swipe.
            - DONE: Goal reached.
            
            RESPONSE FORMAT (JSON ONLY):
            { 
              "id": "next", 
              "thought": "I see the search bar at [500, 120]. I will click it to find the contact.",
              "description": "Searching for contact...", 
              "toolName": "tool", 
              "params": { ... } 
            }
        """.trimIndent()

        val model = SecurePrefs.getSelectedModel(context) ?: "gpt-4o"
        
        // Use customBaseUrl to ensure we use the user's unified provider
        val response = llmClient.getCompletion(apiKey, "What is the next step?", systemPrompt, model, null, baseUrl)
        
        return try {
            val cleanResponse = response.substringAfter("{").substringBeforeLast("}")
            gson.fromJson("{$cleanResponse}", TaskStep::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getToolDefinitions(): JSONArray {
        val jsonArray = JSONArray()
        val tools = skillManager.get().getToolDefinitions().split("\n")
        tools.forEach { tool ->
            if (tool.isNotBlank()) {
                jsonArray.put(tool.removePrefix("- ").trim())
            }
        }
        return jsonArray
    }

    fun getToolDefinitionsText(): String {
        return skillManager.get().getToolDefinitions()
    }
}
