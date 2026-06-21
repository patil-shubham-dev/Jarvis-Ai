package com.jarvisai.app.data.repository

import android.content.Context
import android.util.Log
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.models.ChatMessageEntity
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
            entities.map { ChatMessage(it.id, it.sessionId, it.role, it.content, it.timestamp, it.isSystemUpdate) }
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
                timestamp = msg.timestamp,
                isSystemUpdate = msg.isSystemUpdate
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

    fun isActionIntent(text: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "how are you", "what's up", "good morning", "good afternoon", "good evening", "jarvis")
        val lowerText = text.lowercase().trim().replace(Regex("[^a-z\\s]"), "")
        
        // If it's just a greeting, it's NOT an action intent
        if (greetings.any { it == lowerText }) return false
        
        // Heuristic for potential actions/questions that need the agent
        val actionKeywords = listOf(
            "open", "send", "call", "play", "set", "search", "show", "tell", 
            "check", "who", "where", "when", "how", "what", "find", "start", "stop"
        )
        
        return actionKeywords.any { lowerText.contains(it) } || lowerText.split(" ").size > 3
    }

    private suspend fun getBaseSystemPrompt(userInput: String): String {
        val relevantMemories = semanticMemory.search(userInput)
        val socialContext = memoryManager.getModuleFiles("SOCIAL_GRAPH").take(5).map { it.nameWithoutExtension }
        
        return buildString {
            append("You are JARVIS, a sophisticated, emotionally intelligent AI operating system for Android.\n")
            append("IDENTITY: You are calm, confident, and proactive. You are an invisible layer assisting the user.\n")
            append("TONE: Short, intelligent, natural, and premium. Avoid robotic phrases like 'Executing task'.\n")
            append("RULES:\n")
            append("- ALWAYS acknowledge the user's request conversationally first.\n")
            append("- For actions like 'open WhatsApp', say 'Opening WhatsApp' or 'On it'.\n")
            append("- For casual greetings, be warm and concise.\n")
            append("- If a tool is needed, the next step will handle it, but you MUST provide the text acknowledgement NOW.\n")
            
            append("\nAVAILABLE CAPABILITIES (For your awareness):\n")
            append(plannerAgent.getToolDefinitionsText())
            append("\n")

            append("\nSOCIAL CONTEXT (Recent Contacts/People):\n")
            if (socialContext.isNotEmpty()) {
                append("- ${socialContext.joinToString(", ")}\n")
            } else {
                append("- No recent contacts found.\n")
            }

            append("\nRELEVANT MEMORIES:\n")
            relevantMemories.forEach { append("- ${it.text}\n") }
            
            append("\nDEVICE STATE:\n")
            append(contextEngine.getCurrentContext())
            
            append("\nEXECUTION HISTORY:\n")
            append(executionTracker.getHistorySummary())
            
            append("\nPRONOUN RESOLUTION: If the user says 'him', 'her', or 'them', refer to the SOCIAL CONTEXT or EXECUTION HISTORY to identify the target.\n")
        }
    }

    fun analyzeVisualContext(sessionId: String, base64Image: String): Flow<String> = flow {
        val apiKey = SecurePrefs.getVisionApiKey(context).ifBlank { SecurePrefs.getApiKey(context) }
        val baseUrl = SecurePrefs.getVisionBaseUrl(context).ifBlank { null }
        val prompt = "Analyze the screen state. Identify active apps, navigation context, and interactive elements."
        
        val visionModel = SecurePrefs.getVisionModel(context).ifBlank { "gpt-4o" }
        val resolvedModel = ModelDetector.resolveModel(apiKey, visionModel, isVision = true).id
        
        emitAll(
            llmClient.getCompletionStream(
                apiKey = apiKey,
                prompt = prompt,
                systemContext = "You are the JARVIS Vision Engine.",
                model = resolvedModel,
                customBaseUrl = baseUrl,
                base64Image = base64Image
            )
        )
    }.flowOn(Dispatchers.IO)

    fun listenToResponse(sessionId: String, history: List<ChatMessage>, prompt: String): Flow<String> = flow {
        val apiKey = SecurePrefs.getApiKey(context)
        val modelName = SecurePrefs.getSelectedModel(context) ?: "gpt-4o"
        val baseUrl = SecurePrefs.getBaseUrl(context)
        
        val resolvedModel = ModelDetector.resolveModel(apiKey, modelName).id
        val systemPrompt = getBaseSystemPrompt(prompt)
        val tools = plannerAgent.getToolDefinitions()

        try {
            emitAll(
                llmClient.getCompletionStream(
                    apiKey = apiKey,
                    prompt = prompt,
                    systemContext = systemPrompt,
                    model = resolvedModel,
                    toolsJson = tools,
                    customBaseUrl = baseUrl
                ).catch { e ->
                    Log.e("ChatRepository", "Stream error", e)
                    emit("I'm sorry, I'm having a bit of trouble connecting right now. One moment...")
                }
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Primary model failed", e)
            emit("I'm having a slight connection issue. One moment while I try to reconnect.")
        }
    }.flowOn(Dispatchers.IO)


    suspend fun updateLastMessage(sessionId: String, newContent: String) {
        val lastMsg = chatDao.getLastMessageForSession(sessionId)
        if (lastMsg != null) {
            chatDao.updateMessageContent(lastMsg.id, newContent)
            sessionDao.updateSessionPreview(sessionId, newContent, System.currentTimeMillis())
        }
    }
}
