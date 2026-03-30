package com.jarvisai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.data.repository.ChatRepository
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import android.app.Application

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository
) : ViewModel() {

    private val _activeSessionId = MutableStateFlow("DEFAULT_SESSION")
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

            // Create placeholder
            val assistantMsg = ChatMessage(sessionId = currentSession, role = MessageRole.JARVIS, content = "")
            repository.saveMessage(assistantMsg)
            
            val accumulated = StringBuilder()
            var lastUpdate = System.currentTimeMillis()

            val apiKey = SecurePrefs.getApiKey(application)
            if (apiKey.isEmpty()) {
                _isLoading.value = false
                repository.updateLastMessage(currentSession, "Please add your API key in settings")
                return@launch
            }

            try {
                repository.listenToResponse(messages.value, text)
                    .collect { token ->
                        accumulated.append(token)
                        
                        if (System.currentTimeMillis() - lastUpdate > 200) {
                            repository.updateLastMessage(currentSession, accumulated.toString())
                            lastUpdate = System.currentTimeMillis()
                        }
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                val errorMsg = "Error: ${e.message}"
                repository.updateLastMessage(currentSession, errorMsg)
                return@launch
            }
            
            // Final update to persist full response (only if successful)
            if (accumulated.isNotEmpty()) {
                repository.updateLastMessage(currentSession, accumulated.toString())
            }
            _isLoading.value = false
            generateSmartTitle(currentSession, text, accumulated.toString().take(100))
        }
    }

    private fun generateSmartTitle(sessionId: String, userText: String, aiText: String) {
        // Implementation for Step 2: Smart Title Generation coming next...
    }

    fun loadSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun startNewChat() {
        _activeSessionId.value = UUID.randomUUID().toString()
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }
}
