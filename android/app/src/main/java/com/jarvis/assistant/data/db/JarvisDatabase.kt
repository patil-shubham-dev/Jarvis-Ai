package com.jarvis.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jarvis.assistant.data.models.*

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, VaultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile private var instance: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase = instance ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, JarvisDatabase::class.java, "jarvis.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
