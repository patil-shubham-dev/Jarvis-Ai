package com.jarvisai.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val latitude: Double? = null,
    val longitude: Double? = null
)
