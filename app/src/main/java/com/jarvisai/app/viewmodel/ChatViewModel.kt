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
import kotlinx.coroutines.flow.collect
import java.util.UUID
import javax.inject.Inject
import android.app.Application
import android.util.Log

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val voiceEngine: VoiceEngine,
    private val skillManager: com.jarvisai.app.core.skills.SkillManager
) : ViewModel() {

    init {
        observeStatusUpdates()
    }

    private fun observeStatusUpdates() {
        viewModelScope.launch {
            skillManager.statusFlow.collect { status: String ->
                saveStatusMessage(status)
            }
        }
    }

    private suspend fun saveStatusMessage(status: String) {
        repository.saveMessage(
            ChatMessage(
                sessionId = _activeSessionId.value,
                role = MessageRole.JARVIS,
                content = status,
                isSystemUpdate = true
            )
        )
    }
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
            
            val apiKey = SecurePrefs.getApiKey(application)
            if (apiKey.isEmpty()) {
                _isLoading.value = false
                repository.saveMessage(ChatMessage(sessionId = currentSession, role = MessageRole.JARVIS, content = "Please add your API key in settings"))
                return@launch
            }

            var fullResponse = ""
            var hasSavedPlaceholder = false

            try {
                // 1. Show Thinking Indicator & Start Loading
                repository.saveMessage(ChatMessage(
                    sessionId = currentSession,
                    role = MessageRole.JARVIS,
                    content = THINKING_PLACEHOLDER
                ))
                
                // 2. Collect response in parallel with a minimum 'thinking' delay
                var accumulatedText = ""
                val collectionJob = launch {
                    repository.listenToResponse(currentSession, messages.value, text)
                        .collect { token ->
                            accumulatedText += token
                        }
                }
                
                // Reduced delay for "Very Fast" feel
                val dynamicDelay = (100..300).random().toLong()
                delay(dynamicDelay) 
                collectionJob.join() // Wait for the rest of the stream if it's still coming

                if (accumulatedText.isBlank()) {
                    repository.updateLastMessage(currentSession, "I'm having a bit of trouble hearing that. Could you say it again?")
                } else {
                    // 3. Fake Streaming Phase (After complete response received)
                    _isLoading.value = false
                    
                    val words = accumulatedText.split(" ")
                    var displayedText = ""
                    
                    // Reset placeholder with empty string before starting typing
                    repository.updateLastMessage(currentSession, "")
                    
                    // Type out word by word for a smooth premium feel
                    for (i in words.indices) {
                        displayedText += words[i] + if (i < words.size - 1) " " else ""
                        
                        // Add a cursor character during typing
                        // Ultra-fast typing for "Very Fast" feel
                        repository.updateLastMessage(currentSession, displayedText + " ▌")
                        delay(10) 
                    }
                    
                    // Remove cursor at the end with a small final delay
                    delay(300)
                    repository.updateLastMessage(currentSession, displayedText)

                    if (SecurePrefs.isTtsEnabled(application)) {
                        voiceEngine.speak(accumulatedText)
                    }
                    
                    fullResponse = accumulatedText
                    
                    // Trigger automation if needed
                    if (repository.isActionIntent(text)) {
                        val automationResult = repository.processResponseIntents(text)
                        if (automationResult.isNotEmpty() && automationResult != "Task finished." && automationResult != "Task finished") {
                             repository.saveMessage(ChatMessage(
                                sessionId = currentSession,
                                role = MessageRole.JARVIS,
                                content = automationResult,
                                isSystemUpdate = true
                             ))
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in sendMessage", e)
                if (!hasSavedPlaceholder) {
                    repository.saveMessage(ChatMessage(sessionId = currentSession, role = MessageRole.JARVIS, content = "Something unexpected happened. I'm working to fix it."))
                } else {
                    repository.updateLastMessage(currentSession, fullResponse + "\n\n[Communication interrupted]")
                }
            } finally {
                _isLoading.value = false
                generateSmartTitle(currentSession, text, fullResponse.take(100))
            }
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
