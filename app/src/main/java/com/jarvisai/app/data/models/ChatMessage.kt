package com.jarvisai.app.data.models

enum class MessageRole { USER, ASSISTANT, JARVIS, ERROR }

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sessionId: String = "DEFAULT_SESSION",
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemUpdate: Boolean = false
)

data class ChatSession(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)
