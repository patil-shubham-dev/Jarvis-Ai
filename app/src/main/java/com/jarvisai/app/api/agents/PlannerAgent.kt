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

    private val STATIC_SYSTEM_PROMPT = """
        You are JARVIS (Sentinel V4.1), an autonomous Android agent.
        
        RULES:
        - Respond ONLY with valid JSON.
        - Analyze the screen delta to understand state changes.
        - If verification fails twice, attempt a different approach or ABORT.
        - Never tap outside screen bounds.
        
        AVAILABLE TOOLS:
        {tools}
        - DONE: Goal reached. Provide a final summary in 'description'.
        
        OUTPUT SCHEMA:
        {
          "thought": "your reasoning",
          "description": "status update for user",
          "toolName": "tool_name",
          "params": { "key": "value" }
        }
    """.trimIndent()

    private var isRunning = false
    private val conversationHistory = mutableListOf<com.jarvisai.app.api.Message>()

    suspend fun processIntent(userInput: String): String {
        if (isRunning) return "I'm already working on a task."
        isRunning = true
        executionTracker.startGoal(userInput)
        conversationHistory.clear()

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
                val screenNodes = com.jarvisai.app.service.JarvisAccessibilityService.instance?.getScreenNodes() ?: emptyList()
                val treeDiff = screenStateEngine.computeDiff(screenNodes)
                val deviceState = screenStateEngine.getCurrentContextSummary()
                
                // 2. Think
                JarvisOverlayService.instance?.setOrbState(JarvisOverlayService.OrbState.THINKING)
                updateOverlay("Thinking...")
                
                // Post status to chat
                skillManager.get().runSkill("communication", mapOf(
                    "type" to "post_status",
                    "text" to "🤔 Analyzing screen context..."
                ))
                
                val nextStep = decideNextStep(userInput, treeDiff, deviceState)
                
                if (nextStep == null) {
                    updateOverlay("Task stuck")
                    skillManager.get().runSkill("communication", mapOf(
                        "type" to "post_status",
                        "text" to "❌ I'm stuck. Please provide more details or check your connection."
                    ))
                    break
                }

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

                // Add to history for next iteration
                conversationHistory.add(com.jarvisai.app.api.Message("assistant", gson.toJson(nextStep)))
                conversationHistory.add(com.jarvisai.app.api.Message("user", "Result: ${result.message}. Success: ${result.success}"))

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
        treeDiff: com.jarvisai.app.api.context.ScreenStateEngine.TreeDiff, 
        deviceState: String
    ): TaskStep? {
        val apiKey = SecurePrefs.getApiKey(context)
        val baseUrl = SecurePrefs.getBaseUrl(context)
        val availableTools = skillManager.get().getToolDefinitions()

        val systemContext = STATIC_SYSTEM_PROMPT.replace("{tools}", availableTools)
        
        val currentObservation = """
            GOAL: $goal
            DEVICE: $deviceState
            SCREEN_DELTA:
            ${with(screenStateEngine) { treeDiff.toPromptString() }}
        """.trimIndent()

        val currentMessages = conversationHistory.toMutableList().apply {
            add(com.jarvisai.app.api.Message("user", currentObservation))
        }

        val model = SecurePrefs.getSelectedModel(context) ?: "gpt-4o"
        
        val response = try {
            llmClient.getChatCompletion(apiKey, currentMessages, systemContext, model, null, baseUrl)
        } catch (e: Exception) {
            Log.e("PlannerAgent", "LLM Call Failed", e)
            return null
        }
        
        return parseAgentResponse(response)
    }

    private fun parseAgentResponse(raw: String): TaskStep? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        
        return try {
            gson.fromJson(cleaned, TaskStep::class.java)
        } catch (e: Exception) {
            // Fallback: extract first JSON object
            val jsonMatch = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL).find(cleaned)?.value
            try {
                gson.fromJson(jsonMatch, TaskStep::class.java)
            } catch (e2: Exception) {
                Log.e("PlannerAgent", "JSON Parse Failed: $raw", e2)
                null
            }
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
