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
    private val skillManager: SkillManager,
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
            updateOverlay("Analyzing goal: $userInput")
            
            while (iteration < maxIterations) {
                iteration++
                Log.d("PlannerAgent", "Starting iteration $iteration")

                // 1. Observe (Local Fast Vision)
                updateOverlay("Observing screen state...")
                val screenContent = skillManager.runSkill("see_screen", emptyMap()).message
                val deviceState = screenStateEngine.getCurrentContextSummary()
                
                // 2. Think
                updateOverlay("Thinking about next step...")
                val nextStep = decideNextStep(userInput, screenContent, deviceState)
                    ?: break // Goal achieved or stuck

                if (nextStep.toolName == "DONE") {
                    executionTracker.completeGoal()
                    updateOverlay("Task complete! ✅")
                    returnToJarvis(nextStep.description)
                    return nextStep.description
                }

                // 3. Safety Check
                val safetyCheck = safetyEngine.isActionSafe(nextStep)
                if (!safetyCheck.isSafe) {
                    updateOverlay("Safety Block: ${safetyCheck.reason}")
                    executionTracker.failGoal("Safety Block: ${safetyCheck.reason}")
                    return "Safety Pause: ${safetyCheck.reason}"
                }

                // 4. Act
                updateOverlay("Acting: ${nextStep.description}")
                executionTracker.addStep(nextStep.description, nextStep.toolName)
                val result = skillManager.runSkill(nextStep.toolName, nextStep.params)
                executionTracker.updateLastStep(result)

                // 5. Verify & Adapt (Improvement 4: Diagnosis)
                if (!result.success) {
                    updateOverlay("Step failed. Diagnosing...")
                    Log.w("PlannerAgent", "Step failed: ${result.message}. Attempting recovery...")
                    
                    // Call vision specifically to see why it failed
                    val diagnosis = skillManager.runSkill("see_screen", emptyMap()).message
                    updateOverlay("Attempting recovery based on visual state.")
                    delay(1000)
                    actionEngine.execute("press back")
                }
                
                delay(800) // Fast enough but visible
            }
        } catch (e: Exception) {
            updateOverlay("Error: ${e.message}")
            return "Execution Error: ${e.message}"
        } finally {
            isRunning = false
        }

        return "Task finished or reached maximum steps."
    }

    private fun updateOverlay(msg: String) {
        JarvisOverlayService.instance?.updateStatus(msg)
    }

    private suspend fun returnToJarvis(summary: String) {
        delay(1000)
        actionEngine.execute("press home")
        delay(500)
        skillManager.setOverlay(JarvisOverlayService.instance)
        updateOverlay("Done: $summary")
    }

    private suspend fun decideNextStep(
        goal: String, 
        screenObservation: String, 
        deviceState: String
    ): TaskStep? {
        val apiKey = SecurePrefs.getApiKey(context)
        val baseUrl = SecurePrefs.getBaseUrl(context)
        
        // Improvement 1: Dynamic Tooling
        val availableTools = skillManager.getToolDefinitions()

        val systemPrompt = """
            You are the JARVIS Autonomous Brain.
            GOAL: $goal
            $deviceState
            OBSERVATION: $screenObservation
            
            HISTORY:
            ${executionTracker.getHistorySummary()}
            
            Decide the SINGLE next atomic action to get closer to the goal.
            If the goal is achieved, return toolName: "DONE".
            
            Available Tools:
            $availableTools
            
            Return ONLY a JSON TaskStep:
            { "id": "next", "description": "Short description of why", "toolName": "tool", "params": { ... } }
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

    fun getToolDefinitions(): JSONArray = JSONArray()
}
