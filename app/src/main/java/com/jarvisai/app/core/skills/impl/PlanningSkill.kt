package com.jarvisai.app.core.skills.impl

import android.content.Context
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.action.agents.CalendarAgent
import com.jarvisai.app.core.action.agents.SettingsAgent
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult

class PlanningSkill(
    context: Context,
    accessibility: AccessibilityHelper,
    private val settingsAgent: SettingsAgent? = null
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: ""
        
        return when (action.lowercase()) {
            "add_event" -> {
                val title = params["title"] as? String ?: return SkillResult(false, "Missing title", errorType = ErrorType.UNKNOWN)
                val time = params["time"] as? String ?: ""
                val success = CalendarAgent.addEvent(title, time)
                SkillResult(success, if (success) "Event added to calendar" else "Failed to add event")
            }
            "set_timer" -> {
                val duration = params["duration"] as? String ?: return SkillResult(false, "Missing duration", errorType = ErrorType.UNKNOWN)
                val success = settingsAgent?.setTimer(duration) ?: false
                SkillResult(success, if (success) "Timer set for $duration" else "Failed to set timer")
            }
            "set_alarm" -> {
                val time = params["time"] as? String ?: return SkillResult(false, "Missing time", errorType = ErrorType.UNKNOWN)
                val label = params["label"] as? String ?: "Alarm"
                val success = settingsAgent?.setAlarm(time, label) ?: false
                SkillResult(success, if (success) "Alarm set for $time" else "Failed to set alarm")
            }
            "set_reminder" -> {
                val title = params["title"] as? String ?: return SkillResult(false, "Missing title", errorType = ErrorType.UNKNOWN)
                val time = params["time"] as? String ?: ""
                val success = settingsAgent?.setReminder(title, time) ?: false
                SkillResult(success, if (success) "Reminder set: $title" else "Failed to set reminder")
            }
            else -> SkillResult(false, "Unknown planning action: $action. Supported: add_event, set_timer, set_alarm, set_reminder", errorType = ErrorType.UNKNOWN)
        }
    }

    override fun getDefinition(): String {
        return "planning(action, title?, time?, duration?, label?): Manage events, timers, alarms, and reminders. Actions: add_event, set_timer, set_alarm, set_reminder."
    }

    override suspend fun verifyState(): Boolean = true
}
