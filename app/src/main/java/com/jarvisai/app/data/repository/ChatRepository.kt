package com.jarvisai.app.data.repository

import android.content.Context
import android.util.Log
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.models.ChatMessageEntity
import com.jarvisai.app.data.models.ChatSessionEntity
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.ChatSession
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.utils.SecurePrefs
import com.jarvisai.app.data.repository.memory.MemoryManager
import com.jarvisai.app.data.repository.memory.SemanticMemoryStore
import com.jarvisai.app.api.context.ContextEngine
import com.jarvisai.app.api.agents.MemoryAgent
import com.jarvisai.app.api.agents.CommunicationAgent
import com.jarvisai.app.api.agents.PlannerAgent
import com.jarvisai.app.core.skills.ExecutionTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryManager: MemoryManager,
    private val semanticMemory: SemanticMemoryStore,
    private val contextEngine: ContextEngine,
    private val chatDao: ChatDao,
    private val sessionDao: ChatSessionDao,
    private val llmClient: LlmClient,
    private val memoryAgent: MemoryAgent,
    private val communicationAgent: CommunicationAgent,
    private val plannerAgent: PlannerAgent,
    private val executionTracker: ExecutionTracker,
    private val learningEngine: com.jarvisai.app.data.repository.memory.LearningEngine
) {

    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { ChatMessage(it.id, it.sessionId, it.role, it.content, it.timestamp) }
        }
    }

    fun getAllSessions(): Flow<List<ChatSession>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { ChatSession(it.id, it.title, it.lastMessage, it.timestamp) }
        }
    }

    suspend fun clearAllHistory() {
        chatDao.clearChat()
        sessionDao.clearAllSessions()
    }

    suspend fun saveMessage(msg: ChatMessage) {
        chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = msg.sessionId,
                role = msg.role,
                content = msg.content,
                timestamp = msg.timestamp
            )
        )
        sessionDao.updateSessionPreview(msg.sessionId, msg.content, msg.timestamp)
        
        // Trigger lightweight learning if it's an assistant message (end of turn)
        if (msg.role == MessageRole.ASSISTANT || msg.role == MessageRole.JARVIS) {
            triggerLightweightLearning(msg.sessionId)
        }
    }

    private suspend fun triggerLightweightLearning(sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val messages = chatDao.getMessagesBySessionOnce(sessionId).takeLast(10).map { 
                    "${it.role}: ${it.content}"
                }
                learningEngine.extractLightweightMemory(sessionId, messages)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Learning trigger failed", e)
            }
        }
    }

    suspend fun generateAndSaveTitle(sessionId: String, userText: String, aiText: String) {
        val existingSession = sessionDao.getSessionById(sessionId)
        if (existingSession != null && existingSession.title != "New Chat") return

        val prompt = "Generate a concise 3-5 word title for a chat starting with: '$userText'. Response: '$aiText'. Return ONLY the title text."
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isEmpty()) return

        try {
            val title = llmClient.getCompletion(
                apiKey = apiKey,
                prompt = prompt,
                systemContext = "You are a chat title generator.",
                model = "gpt-4o-mini"
            ).trim().removeSurrounding("\"")
            
            sessionDao.updateSessionTitle(sessionId, title)
        } catch (e: Exception) {
            sessionDao.updateSessionTitle(sessionId, userText.take(20) + "...")
        }
    }

    suspend fun processResponseIntents(userInput: String): String {
        return plannerAgent.processIntent(userInput)
    }

    private suspend fun getBaseSystemPrompt(userInput: String): String {
        val relevantMemories = semanticMemory.search(userInput)
        return buildString {
            append("You are JARVIS, a sophisticated autonomous multimodal AI agent for Android.\n")
            append("IDENTITY: You are a professional, proactive operating layer. You combine Perception, Memory, and Action.\n")
            append("CAPABILITIES:\n")
            append("- Eyes: Real-time screen analysis via MediaProjection.\n")
            append("- Hands: Human-like accessibility interaction and gesture injection.\n")
            append("- Memory: Long-term episodic memory for workflows and preferences.\n")
            append("- Planning: Decomposing complex intents into structured task graphs.\n")
            append("\nRELEVANT MEMORIES:\n")
            relevantMemories.forEach { append("- ${it.text}\n") }
            append("\nCURRENT DEVICE STATE:\n")
            append(contextEngine.getCurrentContext())
            append("\nEXECUTION HISTORY:\n")
            append(executionTracker.getHistorySummary())
        }
    }

    fun analyzeVisualContext(sessionId: String, base64Image: String): Flow<String> = flow {
        val apiKey = SecurePrefs.getVisionApiKey(context).ifBlank { SecurePrefs.getApiKey(context) }
        val baseUrl = SecurePrefs.getVisionBaseUrl(context).ifBlank { null }
        val prompt = "Analyze the screen state. Identify active apps, navigation context, and interactive elements."
        
        val visionModel = SecurePrefs.getVisionModel(context).ifBlank { "gpt-4o" }
        val resolvedModel = ModelDetector.resolveModel(apiKey, visionModel, isVision = true).id
        
        emitAll(llmClient.getCompletionStream(apiKey, prompt, "You are the JARVIS Vision Engine.", resolvedModel, baseUrl, base64Image))
    }.flowOn(Dispatchers.IO)

    fun listenToResponse(sessionId: String, history: List<ChatMessage>, prompt: String): Flow<String> = flow {
        val apiKey = SecurePrefs.getApiKey(context)
        val modelName = SecurePrefs.getSelectedModel(context) ?: "gpt-4o"
        val baseUrl = SecurePrefs.getBaseUrl(context)
        
        val resolvedModel = ModelDetector.resolveModel(apiKey, modelName).id
        try {
            val systemPrompt = getBaseSystemPrompt(prompt)
            val tools = plannerAgent.getToolDefinitions()

            // Check if this intent requires autonomous execution
            val response = if (isActionIntent(prompt)) {
                plannerAgent.processIntent(prompt)
            } else {
                llmClient.getCompletion(
                    apiKey = apiKey,
                    prompt = prompt,
                    systemContext = systemPrompt,
                    model = resolvedModel,
                    toolsJson = tools,
                    customBaseUrl = baseUrl
                )
            }
            emit(response)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Primary model failed", e)
            emit("I encountered an issue. Let me try a different approach.")
        }
    }.flowOn(Dispatchers.IO)

    private fun isActionIntent(prompt: String): Boolean {
        val keywords = listOf("play", "open", "send", "message", "click", "type", "scroll", "find", "search")
        return keywords.any { prompt.lowercase().contains(it) }
    }

    suspend fun updateLastMessage(sessionId: String, newContent: String) {
        val lastMsg = chatDao.getLastMessageForSession(sessionId)
        if (lastMsg != null) {
            chatDao.updateMessageContent(lastMsg.id, newContent)
            sessionDao.updateSessionPreview(sessionId, newContent, System.currentTimeMillis())
        }
    }
}
