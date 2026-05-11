package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.util.Log
import com.jarvisai.app.api.vision.LocalVisionEngine
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.SkillResult
import kotlinx.coroutines.*

/**
 * Multimodal Vision skill for Sentinel V4.1.
 * Uses local MobileNet-based engine for high-performance screen analysis.
 */
class VisionSkill(
    context: Context,
    accessibility: AccessibilityHelper,
    private val localVisionEngine: LocalVisionEngine
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        Log.d("VisionSkill", "Starting execution...")
        Log.d("VisionSkill", "Capturing screenshot...")
        updateStatus("Capturing screen...")
        val screenshot = accessibility.captureScreenshot() 
        if (screenshot == null) {
            Log.e("VisionSkill", "Screenshot capture failed (timed out or null)")
            return SkillResult(false, "Failed to capture screen.")
        }
        Log.d("VisionSkill", "Screenshot captured successfully.")

        // Run Vision and Accessibility in parallel for speed
        // Set a strict timeout for the visual part to prevent hanging the whole system
        val analysisResult = try {
            coroutineScope {
                Log.d("VisionSkill", "Starting parallel jobs (Vision + Accessibility)...")
                
                // Screenshot/Vision job with strict 3s timeout
                val visionJob = async(Dispatchers.Default) {
                    withTimeoutOrNull(3000) {
                        localVisionEngine.analyze(screenshot)
                    } ?: "Visual analysis timed out. Using accessibility data only."
                }
                
                // Accessibility job (usually very fast)
                val accessibilityJob = async(Dispatchers.Default) { 
                    accessibility.getScreenContent() 
                }
                
                val v = visionJob.await()
                Log.d("VisionSkill", "Vision job completed/timed out.")
                val a = accessibilityJob.await()
                Log.d("VisionSkill", "Accessibility job completed.")
                Pair(v, a)
            }
        } catch (e: Exception) {
            Log.e("VisionSkill", "Parallel analysis failed", e)
            Pair("Visual analysis failed: ${e.message}", accessibility.getScreenContent())
        }

        val analysis = analysisResult.first
        val accessibilityContent = analysisResult.second

        Log.d("VisionSkill", "Analysis complete. Text length: ${accessibilityContent.length}")
        updateStatus("Scan complete.")
        
        return SkillResult(
            success = true,
            message = "VISION:\n$analysis\n\nACCESSIBILITY:\n$accessibilityContent"
        )
    }

    /**
     * Special mode to find coordinates for a specific visual icon.
     */
    suspend fun findIcon(params: Map<String, Any>): SkillResult {
        val query = params["query"] as? String ?: return SkillResult(false, "No query provided")
        updateStatus("Looking for '$query' icon...")
        
        val screenshot = accessibility.captureScreenshot() ?: return SkillResult(false, "Failed to capture screen")
        val point = localVisionEngine.findObjectCoordinates(screenshot, query)
        
        return if (point != null) {
            SkillResult(true, "Found $query at (${point.x}, ${point.y})", data = mapOf("x" to point.x, "y" to point.y))
        } else {
            SkillResult(false, "Could not visually locate '$query'")
        }
    }

    override fun getDefinition(): String {
        return buildString {
            append("see_screen(): Analyze the current screen state (Fast, Local).\n")
            append("find_icon(query): Find the x, y coordinates of a specific app or icon on the screen.")
        }
    }

    override suspend fun verifyState(): Boolean = true
}
