package com.jarvisai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvisai.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionOnce(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT id FROM chat_messages WHERE sessionId = :sessionId AND (role = 'JARVIS' OR role = 'ASSISTANT') ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAssistantMessageId(sessionId: String): Long?

    @Query("UPDATE chat_messages SET content = :newContent WHERE id = :msgId")
    suspend fun updateMessageContent(msgId: Long, newContent: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChat()
}
