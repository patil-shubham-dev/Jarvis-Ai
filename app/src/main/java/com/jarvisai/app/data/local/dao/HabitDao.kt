package com.jarvisai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jarvisai.app.data.models.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity)

    @Query("SELECT * FROM habits ORDER BY timestamp DESC LIMIT 1000")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT packageName, COUNT(*) as count FROM habits WHERE hourOfDay = :hour GROUP BY packageName ORDER BY count DESC LIMIT 5")
    suspend fun getTopAppsForHour(hour: Int): List<AppUsageCount>

    data class AppUsageCount(
        val packageName: String,
        val count: Int
    )
}
