package com.jarvisai.app.api.agents

/**
 * Common models for autonomous agents.
 */

data class ExecutionGraph(
    val goal: String,
    val steps: List<TaskStep>,
    val currentStepIndex: Int = 0
)

data class TaskStep(
    val id: String?,
    val description: String?,
    val toolName: String?,
    val params: Map<String, Any>?,
    val critical: Boolean? = true
)
