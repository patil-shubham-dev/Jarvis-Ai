package com.jarvisai.app.core.routines

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvisai.app.core.action.ActionEngine
import com.jarvisai.app.data.local.dao.RoutineDao
import com.jarvisai.app.data.models.RoutineEntity
import com.jarvisai.app.service.JarvisOverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routineDao: RoutineDao,
    private val actionEngine: dagger.Lazy<ActionEngine>,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "RoutineEngine"
    }

    suspend fun executeRoutine(routineName: String): Boolean {
        val routine = routineDao.findRoutine("%$routineName%") ?: return false
        return runRoutine(routine)
    }

    suspend fun runRoutine(routine: RoutineEntity): Boolean {
        Log.d(TAG, "Starting routine: ${routine.name}")
        val overlay = JarvisOverlayService.instance
        
        val type = object : TypeToken<List<ActionEngine.IntentParsed>>() {}.type
        val steps: List<ActionEngine.IntentParsed> = gson.fromJson(routine.stepsJson, type)

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Routine: ${routine.name}")

        val timelineSteps = steps.map { step ->
            JarvisOverlayService.TimelineStep(step.type.name.lowercase().replace("_", " "), JarvisOverlayService.StepStatus.PENDING)
        }
        overlay?.updateTimeline(timelineSteps)

        var success = true
        for (i in steps.indices) {
            val step = steps[i]
            
            // Update timeline
            timelineSteps[i].status = JarvisOverlayService.StepStatus.RUNNING
            overlay?.updateTimeline(timelineSteps)
            
            overlay?.updateStatus("Running: ${timelineSteps[i].title}")
            
            val stepSuccess = actionEngine.get().execute(step)
            
            if (stepSuccess) {
                timelineSteps[i].status = JarvisOverlayService.StepStatus.COMPLETED
            } else {
                timelineSteps[i].status = JarvisOverlayService.StepStatus.FAILED
                success = false
                break
            }
            overlay?.updateTimeline(timelineSteps)
            delay(1000) // Small breather between steps
        }

        routineDao.incrementUsage(routine.id)
        
        overlay?.updateStatus(if (success) "Routine Complete" else "Routine Failed")
        delay(2000)
        overlay?.setExecutionMode(false)
        
        return success
    }

    /**
     * Learning Logic: Identify if a set of actions should be a routine
     */
    suspend fun learnRoutine(name: String, steps: List<ActionEngine.IntentParsed>) {
        val json = gson.toJson(steps)
        val entity = RoutineEntity(
            name = name,
            triggerKeyword = name.lowercase(),
            stepsJson = json,
            isAutonomous = true
        )
        routineDao.insert(entity)
        Log.d(TAG, "New routine learned: $name")
    }
}
