package com.jarvisai.app.data.models

import androidx.room.*

@Entity(tableName = "memory_snippets")
data class MemorySnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val embedding: List<Float>,
    val type: String, // "preference" | "fact" | "goal" | "task" | "context"
    val importance: Float, // 0.0 to 1.0
    val module: String, // e.g., "CORE_IDENTITY"
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
