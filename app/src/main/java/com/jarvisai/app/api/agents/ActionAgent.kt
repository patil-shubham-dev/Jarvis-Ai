package com.jarvisai.app.api.agents

import com.jarvisai.app.core.action.ActionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer B: Multi-Agent Orchestration - Action Agent
 * Executes device-level tasks with comprehensive action type support.
 */
@Singleton
class ActionAgent @Inject constructor(
    private val actionEngine: ActionEngine
) {
    suspend fun execute(action: String, parameters: Map<String, String>): String {
        val type = when (action.lowercase()) {
            "open_app" -> ActionEngine.ActionType.OPEN_APP
            "set_reminder" -> ActionEngine.ActionType.SET_REMINDER
            "send_message", "send_whatsapp" -> ActionEngine.ActionType.SEND_MESSAGE
            "search_web" -> ActionEngine.ActionType.SEARCH_WEB
            "set_volume" -> ActionEngine.ActionType.SET_VOLUME
            "set_brightness" -> ActionEngine.ActionType.SET_BRIGHTNESS
            "control_wifi" -> ActionEngine.ActionType.CONTROL_WIFI
            "control_bluetooth" -> ActionEngine.ActionType.CONTROL_BLUETOOTH
            "set_alarm" -> ActionEngine.ActionType.SET_ALARM
            "enable_dnd" -> ActionEngine.ActionType.ENABLE_DND
            "play_music" -> ActionEngine.ActionType.PLAY_MUSIC
            "navigate" -> ActionEngine.ActionType.NAVIGATE
            "scroll" -> ActionEngine.ActionType.SCROLL
            "press_back" -> ActionEngine.ActionType.PRESS_BACK
            "press_home" -> ActionEngine.ActionType.PRESS_HOME
            "share_file" -> ActionEngine.ActionType.SHARE_FILE
            else -> ActionEngine.ActionType.UNKNOWN
        }

        val target = parameters["appName"] ?: parameters["contact"]
            ?: parameters["target"] ?: parameters["query"]
        val content = parameters["message"] ?: parameters["content"]
            ?: parameters["title"] ?: parameters["action"]

        val intent = ActionEngine.IntentParsed(
            type = type,
            target = target,
            content = content,
            timeOrDate = parameters["time"] ?: parameters.getOrDefault("date", "")
        )

        val result = actionEngine.execute(intent)
        return if (result) "Executed: $action on ${target ?: "system"}"
        else "Action failed: $action"
    }
}
