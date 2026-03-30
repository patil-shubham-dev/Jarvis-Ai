package com.jarvisai.app.agents

import com.jarvisai.app.core.action.ActionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer B: Multi-Agent Orchestration - Action Agent
 * Executes device-level tasks (Messages, WhatsApp, Calendar).
 */
@Singleton
class ActionAgent @Inject constructor(
    private val actionEngine: ActionEngine
) {
    /**
     * Executes specified atomic device actions.
     */
    suspend fun execute(action: String, parameters: Map<String, String>): String {
        val type = when (action.lowercase()) {
            "open_app" -> ActionEngine.ActionType.OPEN_APP
            "set_reminder" -> ActionEngine.ActionType.SET_REMINDER
            "send_message" -> ActionEngine.ActionType.SEND_MESSAGE
            else -> ActionEngine.ActionType.UNKNOWN
        }

        val target = parameters["appName"] ?: parameters["contact"] ?: parameters["target"]
        val content = parameters["message"] ?: parameters["content"] ?: parameters["title"]

        val intent = ActionEngine.IntentParsed(
            type = type,
            target = target,
            content = content,
            timeOrDate = parameters["time"] ?: parameters.getOrDefault("date", "")
        )

        val result = actionEngine.execute(intent)
        return if (result) "Executed: $action on $target" else "Action failed: $action"
    }
}
