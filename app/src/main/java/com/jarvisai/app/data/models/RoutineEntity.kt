package com.jarvisai.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerKeyword: String?,
    val stepsJson: String, // Serialized list of ActionEngine.IntentParsed
    val usageCount: Int = 0,
    val lastUsedMillis: Long = System.currentTimeMillis(),
    val isAutonomous: Boolean = false,
    val predictedTimeOfDay: String? = null // e.g., "MORNING", "EVENING"
)
