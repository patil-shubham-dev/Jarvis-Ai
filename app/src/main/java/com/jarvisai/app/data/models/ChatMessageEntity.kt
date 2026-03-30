package com.jarvisai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.jarvisai.app.data.model.MessageRole

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String = "DEFAULT_SESSION",
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
