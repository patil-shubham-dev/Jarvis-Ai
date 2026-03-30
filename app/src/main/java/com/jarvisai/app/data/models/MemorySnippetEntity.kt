package com.jarvisai.app.data.local.entity

import androidx.room.*

@Entity(tableName = "memory_snippets")
data class MemorySnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val embedding: List<Float>,
    val metadata: String, // module name (e.g., "CORE_IDENTITY")
    val timestamp: Long = System.currentTimeMillis()
)
