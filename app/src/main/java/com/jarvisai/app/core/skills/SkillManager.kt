package com.jarvisai.app.core.skills

import android.content.Context
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.impl.WhatsAppSkill
import com.jarvisai.app.core.skills.impl.VisionSkill
import com.jarvisai.app.core.skills.impl.GenericControlSkill
import com.jarvisai.app.core.skills.impl.SpotifySkill
import com.jarvisai.app.service.JarvisOverlayService
import com.jarvisai.app.api.LlmClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val localVisionEngine: com.jarvisai.app.api.vision.LocalVisionEngine
) {
    private val skills = mutableMapOf<String, BaseSkill>()
    private var overlayService: JarvisOverlayService? = null
    
    // Accessibility helper (usually injected or passed from Service)
    private var accessibility: AccessibilityHelper? = null

    fun initialize(accessibilityHelper: AccessibilityHelper) {
        this.accessibility = accessibilityHelper
        registerSkills()
    }

    fun setOverlay(service: JarvisOverlayService?) {
        this.overlayService = service
        skills.values.forEach { it.overlay = service }
    }

    private fun registerSkills() {
        val acc = accessibility ?: return
        
        skills["send_whatsapp"] = WhatsAppSkill(context, acc)
        skills["play_music"] = SpotifySkill(context, acc)
        skills["see_screen"] = VisionSkill(context, acc, localVisionEngine)
        skills["generic_control"] = GenericControlSkill(context, acc)
    }

    suspend fun runSkill(name: String, params: Map<String, Any>): SkillResult {
        val skill = skills[name] ?: return SkillResult(false, "Skill '$name' not found", errorType = ErrorType.UNKNOWN)
        
        return try {
            skill.execute(params)
        } catch (e: Exception) {
            SkillResult(false, "Error executing skill: ${e.message}", errorType = ErrorType.UNKNOWN)
        }
    }

    fun getAvailableSkills(): List<String> = skills.keys.toList()

    /**
     * Improvement 1: Dynamic Skill Discovery.
     * Returns a structured list of available tools for the PlannerAgent.
     */
    fun getToolDefinitions(): String {
        return buildString {
            append("- send_whatsapp(recipient, message): Send a message to a contact.\n")
            append("- play_music(query): Search and play music on Spotify.\n")
            append("- see_screen(): Analyze the current screen state (Fast, Local).\n")
            append("- generic_control(command): Execute system commands like 'press back', 'open settings'.\n")
            append("- open_app(package_name): Launch a specific Android application.\n")
            append("- click_element(query): Click a UI element based on text or description.\n")
            append("- type_text(query, text): Type text into a focused input field.\n")
            append("- scroll(direction): Scroll 'up', 'down', 'left', or 'right'.\n")
        }
    }
}
