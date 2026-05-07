package com.jarvisai.app.data.local.dao

import androidx.room.*
import com.jarvisai.app.data.models.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY usageCount DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE name LIKE :query OR triggerKeyword LIKE :query LIMIT 1")
    suspend fun findRoutine(query: String): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("UPDATE routines SET usageCount = usageCount + 1, lastUsedMillis = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())
}
