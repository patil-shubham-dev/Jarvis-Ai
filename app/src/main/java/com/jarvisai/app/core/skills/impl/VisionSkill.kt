package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.graphics.Bitmap
import com.jarvisai.app.api.vision.LocalVisionEngine
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.SkillResult
import javax.inject.Inject

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
        updateStatus("Scanning screen locally...")
        
        val screenshot = accessibility.captureScreenshot() 
            ?: return SkillResult(false, "Failed to capture screen")

        // No longer using cloud LLM for vision - uses local ML Kit
        val analysis = localVisionEngine.analyze(screenshot)

        // Merge with accessibility data for hybrid understanding
        val accessibilityContent = accessibility.getScreenContent()
        
        return SkillResult(
            success = true,
            message = "VISION:\n$analysis\n\nACCESSIBILITY:\n$accessibilityContent"
        )
    }

    override fun getDefinition(): String {
        return "see_screen(): Analyze the current screen state (Fast, Local)."
    }

    override suspend fun verifyState(): Boolean = true
}
