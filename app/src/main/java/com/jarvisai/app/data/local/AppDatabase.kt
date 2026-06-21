package com.jarvisai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

val MIGRATION_4_5 = Migration(4, 5) { db ->
    db.execSQL("ALTER TABLE reminders ADD COLUMN sessionId TEXT DEFAULT NULL")
}

val MIGRATION_5_6 = Migration(5, 6) { db ->
    db.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUrl TEXT DEFAULT NULL")
}

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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, destructiveFallback: Boolean = false): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val builder = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "jarvis_db"
                    ).addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    if (destructiveFallback) {
                        builder.fallbackToDestructiveMigration()
                    } else {
                        builder.fallbackToDestructiveMigration(false)
                    }
                    builder.build().also { INSTANCE = it }
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return getInstance(context, destructiveFallback = true)
        }
    }
}
