package com.jarvisai.app.data.repository

import com.jarvisai.app.data.local.dao.HabitDao
import com.jarvisai.app.data.models.HabitEntity
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao
) {
    suspend fun logAppUsage(packageName: String) {
        val calendar = Calendar.getInstance()
        val habit = HabitEntity(
            packageName = packageName,
            timestamp = System.currentTimeMillis(),
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        )
        habitDao.insert(habit)
    }

    suspend fun getRoutineSuggestions(): List<String> {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Find apps frequently used at this hour
        return habitDao.getTopAppsForHour(currentHour)
            .filter { it.count >= 3 } // At least 3 occurrences
            .map { it.packageName }
    }
}
