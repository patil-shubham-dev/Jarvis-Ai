package com.jarvisai.app.data.model

enum class MessageRole { USER, ASSISTANT, JARVIS, ERROR }

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sessionId: String = "DEFAULT_SESSION",
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)
