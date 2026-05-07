package com.jarvisai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.data.repository.ChatRepository
import com.jarvisai.app.utils.SecurePrefs
import com.jarvisai.app.core.voice.VoiceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import android.app.Application

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val voiceEngine: VoiceEngine
) : ViewModel() {
    private companion object {
        const val THINKING_PLACEHOLDER = "Jarvis is thinking"
    }

    private val _activeSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    // Dynamically switch message history based on active session
    val messages: StateFlow<List<ChatMessage>> = _activeSessionId.flatMapLatest { sessionId ->
        repository.getMessagesBySession(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All available sessions for the sidebar history
    val sessions = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun analyzeScreen(base64Image: String) {
        val currentSession = _activeSessionId.value
        viewModelScope.launch {
            _isLoading.value = true
            repository.analyzeVisualContext(currentSession, base64Image)
                .collect { token ->
                    repository.updateLastMessage(currentSession, token)
                }
            _isLoading.value = false
        }
    }

    fun sendMessage(text: String) {
        val currentSession = _activeSessionId.value
        val userMsg = ChatMessage(sessionId = currentSession, role = MessageRole.USER, content = text)
        
        viewModelScope.launch {
            repository.saveMessage(userMsg)
            _isLoading.value = true
            var displayedResponse = ""

            // Create placeholder
            val assistantMsg = ChatMessage(
                sessionId = currentSession,
                role = MessageRole.JARVIS,
                content = THINKING_PLACEHOLDER
            )
            repository.saveMessage(assistantMsg)

            val apiKey = SecurePrefs.getApiKey(application)
            if (apiKey.isEmpty()) {
                _isLoading.value = false
                repository.updateLastMessage(currentSession, "Please add your API key in settings")
                return@launch
            }

            try {
                val fullResponse = withTimeout(45000) {
                    repository.listenToResponse(currentSession, messages.value, text).firstOrNull().orEmpty().trim()
                }

                if (fullResponse.isBlank()) {
                    _isLoading.value = false
                    repository.updateLastMessage(currentSession, "No response received. Please check your API key and internet connection.")
                    return@launch
                }

                repository.updateLastMessage(currentSession, fullResponse)
                
                if (SecurePrefs.isTtsEnabled(application)) {
                    voiceEngine.speak(fullResponse)
                }

                val toolSummary = repository.processResponseIntents(fullResponse)?.trim().orEmpty()
                if (toolSummary.isNotEmpty()) {
                    displayedResponse = toolSummary
                    repository.updateLastMessage(currentSession, toolSummary)
                } else if (fullResponse.startsWith("{") && fullResponse.contains("tool_calls")) {
                    displayedResponse = "I couldn't complete that device action."
                    repository.updateLastMessage(currentSession, displayedResponse)
                } else {
                    displayedResponse = fullResponse
                }
            } catch (e: TimeoutCancellationException) {
                _isLoading.value = false
                repository.updateLastMessage(currentSession, "Request timed out. Please try again or switch model in Settings.")
                return@launch
            } catch (e: Exception) {
                _isLoading.value = false
                val errorMsg = "Error: ${e.message}"
                repository.updateLastMessage(currentSession, errorMsg)
                return@launch
            }
            
            // Final update to persist full response (only if successful)
            _isLoading.value = false
            generateSmartTitle(currentSession, text, displayedResponse.take(100))
        }
    }

    private fun generateSmartTitle(sessionId: String, userText: String, aiText: String) {
        viewModelScope.launch {
            repository.generateAndSaveTitle(sessionId, userText, aiText)
        }
    }

    fun loadSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun startNewChat() {
        _activeSessionId.value = UUID.randomUUID().toString()
    }

    fun startFreshChatForLaunch() {
        startNewChat()
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }
}
