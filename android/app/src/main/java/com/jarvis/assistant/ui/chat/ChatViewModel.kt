package com.jarvis.assistant.ui.chat

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.api.ClaudeApi
import com.jarvis.assistant.core.commands.CommandExecutor
import com.jarvis.assistant.core.prefs.Prefs
import com.jarvis.assistant.data.models.MessageEntity
import com.jarvis.assistant.data.repository.MemoryExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class UiMessage(
    val id: Long = System.currentTimeMillis(),
    val role: String,
    val text: String,
    val isCommand: Boolean = false
)

sealed class ChatState {
    object Idle : ChatState()
    object Thinking : ChatState()
    object Streaming : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel(app: Application) : AndroidViewModel(app), TextToSpeech.OnInitListener {

    private val db = (app as JarvisApp).db
    val prefs = Prefs(app)
    private val api = ClaudeApi()
    private val executor = CommandExecutor(app)
    private var tts = TextToSpeech(app, this)
    private var ttsReady = false

    private val _state = MutableStateFlow<ChatState>(ChatState.Idle)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _pendingCommand = MutableStateFlow<CommandExecutor.Result?>(null)
    val pendingCommand: StateFlow<CommandExecutor.Result?> = _pendingCommand.asStateFlow()

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    var ttsSessionEnabled = true
    var lastInputWasVoice = false

    init {
        if (prefs.currentSessionId.isBlank()) prefs.newSession()
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val msgs = db.messageDao().getLastN(prefs.currentSessionId, 20)
            _messages.value = msgs.reversed().map { UiMessage(it.id, it.role, it.content, it.isCommand) }
            msgs.reversed().takeLast(10).forEach { conversationHistory.add(it.role to it.content) }
        }
    }

    fun send(text: String, fromVoice: Boolean = false) {
        if (prefs.apiKey.isBlank()) {
            appendMessage(UiMessage(role = "assistant", text = "Please add your Claude API key in Settings to activate me."))
            return
        }
        lastInputWasVoice = fromVoice
        val userMsg = UiMessage(role = "user", text = text)
        appendMessage(userMsg)
        persistMessage("user", text)
        _state.value = ChatState.Thinking

        viewModelScope.launch {
            try {
                val memories = db.memoryDao().getRecent()
                var accumulated = ""
                val streamId = System.currentTimeMillis()
                appendMessage(UiMessage(id = streamId, role = "assistant", text = ""))

                api.streamChat(text, prefs.apiKey, prefs.model, conversationHistory, memories)
                    .collect { chunk ->
                        accumulated += chunk
                        val display = accumulated.substringBefore("<<<MEMORY>>>").trim()
                        updateMessage(streamId, display)
                        _state.value = ChatState.Streaming
                    }

                val extracted = MemoryExtractor.extract(accumulated)
                val cleanText = extracted.cleanText
                val cmdResult = executor.tryExecute(cleanText)

                when {
                    cmdResult.requiresConfirmation -> {
                        updateMessage(streamId, cmdResult.speech, isCommand = true)
                        _pendingCommand.value = cmdResult
                        speakIfNeeded(cmdResult.speech, fromVoice, isCommand = true)
                    }
                    cmdResult.isCommand -> {
                        updateMessage(streamId, cmdResult.speech, isCommand = true)
                        speakIfNeeded(cmdResult.speech, fromVoice, isCommand = true)
                    }
                    else -> {
                        updateMessage(streamId, cleanText)
                        speakIfNeeded(cleanText, fromVoice, isCommand = false)
                    }
                }

                extracted.memories.forEach { db.memoryDao().upsert(it) }
                conversationHistory.add("user" to text)
                conversationHistory.add("assistant" to cleanText)
                if (conversationHistory.size > 20) repeat(2) { conversationHistory.removeAt(0) }
                persistMessage("assistant", cleanText, cmdResult.isCommand)
                _state.value = ChatState.Idle

            } catch (e: Exception) {
                val msg = mapError(e)
                appendMessage(UiMessage(role = "assistant", text = msg))
                _state.value = ChatState.Error(msg)
            }
        }
    }

    fun confirmPending() {
        val cmd = _pendingCommand.value ?: return
        _pendingCommand.value = null
        if (cmd.pendingAction != null && cmd.pendingParams != null) {
            executor.executeConfirmed(cmd.pendingAction, cmd.pendingParams, cmd.pendingJson)
        }
    }

    fun dismissPending() { _pendingCommand.value = null }

    fun newSession() {
        prefs.newSession()
        _messages.value = emptyList()
        conversationHistory.clear()
        _state.value = ChatState.Idle
    }

    fun speak(text: String) {
        if (ttsReady) { tts.stop(); tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
    }

    fun stopSpeaking() { if (ttsReady) tts.stop() }

    private fun speakIfNeeded(text: String, fromVoice: Boolean, isCommand: Boolean) {
        if ((fromVoice || isCommand || ttsSessionEnabled) && prefs.ttsEnabled && ttsReady) {
            tts.stop()
            tts.speak(text.take(250), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun appendMessage(msg: UiMessage) {
        _messages.value = _messages.value + msg
    }

    private fun updateMessage(id: Long, text: String, isCommand: Boolean = false) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(text = text, isCommand = isCommand) else it
        }
    }

    private fun persistMessage(role: String, content: String, isCommand: Boolean = false) {
        viewModelScope.launch {
            db.messageDao().insert(MessageEntity(role = role, content = content,
                sessionId = prefs.currentSessionId, isCommand = isCommand))
        }
    }

    private fun mapError(e: Exception) = when {
        e.message?.contains("401") == true -> "Invalid API key — please check Settings."
        e.message?.contains("529") == true -> "Claude is overloaded. Try again shortly."
        e.message?.contains("UnknownHost") == true -> "No internet connection."
        else -> "Error: ${e.message}"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(prefs.ttsSpeed)
            tts.setPitch(prefs.ttsPitch)
            ttsReady = true
        }
    }

    override fun onCleared() {
        tts.stop(); tts.shutdown(); super.onCleared()
    }
}
