package com.jarvisai.app.core.skills.impl

import android.content.Context
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.action.agents.CalendarAgent
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult

class PlanningSkill(
    context: Context,
    accessibility: AccessibilityHelper
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: "add_event"
        val title = params["title"] as? String ?: return SkillResult(false, "Missing title", errorType = ErrorType.UNKNOWN)
        val time = params["time"] as? String ?: ""
        
        return when (action.lowercase()) {
            "add_event" -> {
                val success = CalendarAgent.addEvent(title, time)
                SkillResult(success, if (success) "Event added to calendar" else "Failed to add event")
            }
            else -> SkillResult(false, "Unknown planning action: $action", errorType = ErrorType.UNKNOWN)
        }
    }

    override fun getDefinition(): String {
        return "planning(action, title, time?): Manage calendar events. Action is 'add_event'."
    }

    override suspend fun verifyState(): Boolean = true
}
