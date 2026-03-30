package com.jarvisai.app.data.repository

import android.content.Context
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.api.LlmClient
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.local.entity.ChatMessageEntity
import com.jarvisai.app.data.local.entity.ChatSessionEntity
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.ChatSession
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.utils.SecurePrefs
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
    private val contextEngine: com.jarvisai.app.core.context.ContextEngine,
    private val chatDao: ChatDao,
    private val sessionDao: ChatSessionDao,
    private val llmClient: LlmClient,
    private val memoryAgent: com.jarvisai.app.api.agents.MemoryAgent,
    private val communicationAgent: com.jarvisai.app.api.agents.CommunicationAgent
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
                content = msg.content
            )
        )
        // Auto-create/update session title
        sessionDao.insertSession(
            ChatSessionEntity(
                id = msg.sessionId,
                title = if (msg.content.length > 20) msg.content.take(20) + "..." else msg.content,
                lastMessage = msg.content.take(40),
                timestamp = System.currentTimeMillis()
            )
        )
        if (msg.role == MessageRole.USER) {
            memoryAgent.memorize("user_chat", msg.content, "COMMUNICATIONS")
        }
    }

    fun analyzeVisualContext(sessionId: String, base64: String): Flow<String> {
        val apiKey = SecurePrefs.getApiKey(context)
        val prompt = "Analyze this screenshot. What app is this, and what's on the screen? Suggest how I can assist with this visual data."
        
        return llmClient.getCompletionStream(
            apiKey = apiKey,
            prompt = prompt,
            systemContext = "You are Jarvis Vision. Describe accurately and proactively.",
            model = "gpt-4o"
        )
    }

    /**
     * JARVIS CORE INTELLIGENCE LOOP: 
     * 1. Semantic Recall 
     * 2. Context Aggregation 
     * 3. AI Generation
     */
    fun listenToResponse(history: List<ChatMessage>, prompt: String): Flow<String> = kotlinx.coroutines.flow.flow {
        val apiKey = SecurePrefs.getApiKey(context)
        val providerInfo = ModelDetector.detect(apiKey)
        val modelName = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("selected_model", "")?.split(" ")?.firstOrNull() 
            ?: providerInfo.models.firstOrNull()?.id ?: "gpt-4o-mini"

        val recalledMemory = if (apiKey.isNotEmpty()) {
            try {
                memoryAgent.recallSemanticContext(prompt)
            } catch (e: Exception) { "" }
        } else ""
        
        val systemPrompt = communicationAgent.buildSystemPrompt(
            recalledMemory = recalledMemory,
            currentContext = contextEngine.getCurrentContext()
        )

        emitAll(llmClient.getCompletionStream(
            apiKey = apiKey,
            prompt = prompt,
            systemContext = systemPrompt,
            model = modelName
        ))
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    suspend fun updateLastMessage(sessionId: String, newContent: String) {
        val lastAid = chatDao.getLastAssistantMessageId(sessionId)
        if (lastAid != null) {
            chatDao.updateMessageContent(lastAid, newContent)
        }
    }

    suspend fun generateAndSaveTitle(sessionId: String, userText: String, aiText: String) {
        val apiKey = SecurePrefs.getApiKey(context)
        if (apiKey.isEmpty()) return

        val prompt = "Based on this first interaction, provide a 3-4 word title for this chat theme. ONLY output the title.\nUser: $userText\nAI: $aiText"
        
        try {
            val title = llmClient.getCompletion(
                apiKey = apiKey,
                prompt = prompt,
                systemContext = "You are a concise title generator.",
                model = "gpt-4o-mini"
            ).removePrefix("\"").removeSuffix("\"").trim()
            
            if (title.isNotEmpty()) {
                sessionDao.updateSessionTitle(sessionId, title)
            }
        } catch (e: Exception) {}
    }
}
