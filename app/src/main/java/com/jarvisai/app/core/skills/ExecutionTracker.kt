package com.jarvisai.app.core.skills

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the current state of autonomous execution.
 * Allows JARVIS to know "What was I doing?" and "What went wrong?".
 */
@Singleton
class ExecutionTracker @Inject constructor() {
    
    data class State(
        val goal: String,
        val steps: MutableList<Step> = mutableListOf(),
        val status: Status = Status.IDLE,
        val startTime: Long = System.currentTimeMillis()
    )

    data class Step(
        val description: String?,
        val skillName: String?,
        val result: SkillResult? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class Status {
        IDLE, IN_PROGRESS, COMPLETED, FAILED
    }

    private var currentState: State? = null

    fun startGoal(goal: String) {
        currentState = State(goal, status = Status.IN_PROGRESS)
        Log.d("ExecutionTracker", "New Goal: $goal")
    }

    fun addStep(description: String?, skillName: String?) {
        currentState?.steps?.add(Step(description ?: "Action", skillName ?: "unknown"))
    }

    fun updateLastStep(result: SkillResult) {
        val lastStep = currentState?.steps?.lastOrNull()
        if (lastStep != null) {
            val updated = lastStep.copy(result = result)
            currentState?.steps?.removeAt(currentState!!.steps.size - 1)
            currentState?.steps?.add(updated)
            
            if (!result.success) {
                Log.e("ExecutionTracker", "Step Failed: ${result.message}")
            }
        }
    }

    fun completeGoal() {
        currentState = currentState?.copy(status = Status.COMPLETED)
        Log.d("ExecutionTracker", "Goal Completed: ${currentState?.goal}")
    }

    fun failGoal(reason: String) {
        currentState = currentState?.copy(status = Status.FAILED)
        Log.e("ExecutionTracker", "Goal Failed: ${currentState?.goal} | Reason: $reason")
    }

    fun getCurrentState(): State? = currentState

    fun getHistorySummary(): String {
        val state = currentState ?: return "No active goal."
        return buildString {
            append("Current Goal: ${state.goal}\n")
            append("Status: ${state.status}\n")
            append("Steps:\n")
            state.steps.forEachIndexed { i, step ->
                append("${i+1}. ${step.description} -> ${step.result?.message ?: "Pending"}\n")
            }
        }
    }
}
