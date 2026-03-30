package com.jarvisai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.local.dao.MemoryDao
import com.jarvisai.app.data.local.entity.ChatMessageEntity
import com.jarvisai.app.data.local.entity.ChatSessionEntity
import com.jarvisai.app.data.local.entity.MemorySnippetEntity

@Database(
    entities = [
        ChatMessageEntity::class, 
        ChatSessionEntity::class,
        MemorySnippetEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun memoryDao(): MemoryDao
}
