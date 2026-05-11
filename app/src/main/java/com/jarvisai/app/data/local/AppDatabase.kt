package com.jarvisai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.local.dao.MemoryDao
import com.jarvisai.app.data.local.dao.ReminderDao
import com.jarvisai.app.data.local.dao.RoutineDao
import com.jarvisai.app.data.models.ChatMessageEntity
import com.jarvisai.app.data.models.ChatSessionEntity
import com.jarvisai.app.data.models.MemorySnippetEntity
import com.jarvisai.app.data.models.ReminderEntity
import com.jarvisai.app.data.models.RoutineEntity

@Database(
    entities = [
        ChatMessageEntity::class, 
        ChatSessionEntity::class,
        MemorySnippetEntity::class,
        ReminderEntity::class,
        RoutineEntity::class,
        com.jarvisai.app.data.models.HabitEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun habitDao(): com.jarvisai.app.data.local.dao.HabitDao
}
