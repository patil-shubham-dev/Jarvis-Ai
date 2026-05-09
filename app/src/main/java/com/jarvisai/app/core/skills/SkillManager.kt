package com.jarvisai.app.core.skills

import android.content.Context
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.impl.WhatsAppSkill
import com.jarvisai.app.core.skills.impl.VisionSkill
import com.jarvisai.app.core.skills.impl.GenericControlSkill
import com.jarvisai.app.core.skills.impl.SpotifySkill
import com.jarvisai.app.core.skills.impl.CommunicationSkill
import com.jarvisai.app.core.skills.impl.PlanningSkill
import com.jarvisai.app.service.JarvisOverlayService
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val localVisionEngine: com.jarvisai.app.api.vision.LocalVisionEngine,
    private val chatRepository: ChatRepository
) {
    private val _statusFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val statusFlow: kotlinx.coroutines.flow.Flow<String> = _statusFlow.asSharedFlow()

    private val skills = mutableMapOf<String, BaseSkill>()
    private var overlayService: JarvisOverlayService? = null

    suspend fun postStatus(message: String) {
        _statusFlow.emit(message)
    }
    
    // Accessibility helper (usually injected or passed from Service)
    private var accessibility: AccessibilityHelper? = null

    fun initialize(accessibilityHelper: AccessibilityHelper) {
        this.accessibility = accessibilityHelper
        registerSkills()
    }

    fun setOverlay(service: JarvisOverlayService?) {
        this.overlayService = service
        skills.values.forEach { 
            it.overlay = service 
            it.skillManager = this
        }
    }

    private fun registerSkills() {
        val acc = accessibility ?: return
        
        skills["send_whatsapp"] = WhatsAppSkill(context, acc)
        skills["play_music"] = SpotifySkill(context, acc)
        skills["see_screen"] = VisionSkill(context, acc, localVisionEngine)
        val generic = GenericControlSkill(context, acc)
        skills["generic_control"] = generic
        skills["open_app"] = generic // Alias for easier use
        skills["tap_at"] = generic
        skills["swipe_at"] = generic
        skills["type_at"] = generic
        skills["communication"] = CommunicationSkill(context, acc)
        skills["planning"] = PlanningSkill(context, acc)
        skills["meeting_memo"] = com.jarvisai.app.core.skills.impl.MeetingMemoSkill(context, acc, chatRepository)
        
        skills.values.forEach { it.skillManager = this }
    }

    suspend fun runSkill(name: String, params: Map<String, Any>): SkillResult {
        val skill = skills[name] ?: return SkillResult(false, "Skill '$name' not found", errorType = ErrorType.UNKNOWN)
        
        skill.currentSkillName = name
        
        val enhancedParams = params.toMutableMap()
        if (!enhancedParams.containsKey("tool_action")) {
            enhancedParams["tool_action"] = name
        }

        return try {
            skill.execute(enhancedParams)
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
            skills.values.forEach { skill ->
                append("- ${skill.getDefinition()}\n")
            }
            // Add core system actions that aren't formal skills yet
            append("- open_app(package_name): Launch a specific Android application.\n")
            append("- click_element(query): Click a UI element based on text or description.\n")
            append("- type_text(query, text): Type text into a focused input field.\n")
            append("- scroll(direction): Scroll 'up', 'down', 'left', or 'right'.\n")
        }
    }
}
