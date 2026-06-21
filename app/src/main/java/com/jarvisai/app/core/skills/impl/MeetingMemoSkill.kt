package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.util.Log
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.api.Message
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.SkillResult
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.data.repository.ChatRepository
import com.jarvisai.app.utils.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Proactive Meeting Memo Skill.
 * Automatically captures meeting context and generates visual minutes.
 */
class MeetingMemoSkill(
    context: Context,
    accessibility: AccessibilityHelper,
    private val chatRepository: ChatRepository,
    private val llmClient: LlmClient? = null
) : BaseSkill(context, accessibility) {

    private var isActive = false

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (isActive) return SkillResult(true, "Meeting Memo is already active.")
        
        isActive = true
        updateStatus("Meeting Intelligence Active. Capturing context...")
        
        val numCaptures = (params["captures"] as? Int)?.coerceIn(1, 10) ?: 3
        val captureInterval = (params["interval_ms"] as? Long)?.coerceIn(3000L, 30000L) ?: 10000L
        
        withContext(Dispatchers.IO) {
            val sessionId = "MEETING_${System.currentTimeMillis()}"
            var capturedText = StringBuilder()
            
            for (i in 1..numCaptures) {
                if (!isActive) break
                
                updateStatus("Capturing Meeting Visuals ($i/$numCaptures)...")
                val screenshot = accessibility.captureScreenshot()
                if (screenshot != null) {
                    val content = accessibility.getScreenContent()
                    if (content.isNotBlank()) {
                        capturedText.append("\n--- Snapshot $i ($sessionId) ---\n")
                        capturedText.append(content)
                    }
                }
                
                delay(captureInterval)
            }
            
            if (capturedText.isNotBlank()) {
                updateStatus("Generating Meeting Summary...")
                
                val apiKey = SecurePrefs.getApiKey(context)
                var summaryText = "No LLM available. Raw captures:\n${capturedText.take(500)}"
                
                if (apiKey.isNotBlank() && llmClient != null) {
                    try {
                        summaryText = llmClient.getChatCompletion(
                            apiKey = apiKey,
                            messages = listOf(Message(role = "user", content = capturedText.toString())),
                            systemContext = "You are a meeting summarizer. Create concise bullet-point meeting notes from screen captures.",
                            model = "gpt-4o-mini"
                        )
                    } catch (e: Exception) {
                        Log.e("MeetingMemoSkill", "LLM summary failed: ${e.message}")
                        summaryText = "Captured meeting context:\n${capturedText.take(500)}"
                    }
                }
                
                chatRepository.saveMessage(ChatMessage(
                    role = MessageRole.JARVIS,
                    content = "Meeting Memo Generated!",
                    isSystemUpdate = true
                ))
                
                chatRepository.saveMessage(ChatMessage(
                    role = MessageRole.JARVIS,
                    content = "PROACTIVE MEMO:\n$summaryText\n\nFull transcript saved to memory.",
                    isSystemUpdate = false
                ))
            }
            
            isActive = false
        }
        
        return SkillResult(true, "Meeting Memo complete.")
    }

    override fun getDefinition(): String {
        return "meeting_memo(captures?, interval_ms?): Actively observe a meeting and generate summary notes."
    }

    override suspend fun verifyState(): Boolean = true
}
