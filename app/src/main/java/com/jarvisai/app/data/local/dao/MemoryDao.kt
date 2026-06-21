package com.jarvisai.app.data.local.dao

import androidx.room.*
import com.jarvisai.app.data.models.MemorySnippetEntity

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: MemorySnippetEntity)

    @Query("SELECT * FROM memory_snippets ORDER BY timestamp DESC")
    suspend fun getAllSnippets(): List<MemorySnippetEntity>

    @Query("SELECT * FROM memory_snippets ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnippets(limit: Int = 200): List<MemorySnippetEntity>

    @Query("DELETE FROM memory_snippets WHERE id = :id")
    suspend fun deleteSnippet(id: Long)

    @Query("DELETE FROM memory_snippets")
    suspend fun clearAll()
}
