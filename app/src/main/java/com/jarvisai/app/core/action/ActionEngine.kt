package com.jarvisai.app.core.action

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ActionEngine handles device-level automation tasks.
 * This is a stub implementation that can be extended with actual device control logic.
 */
@Singleton
class ActionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ActionType {
        OPEN_APP,
        SET_REMINDER,
        SEND_MESSAGE,
        SEARCH_WEB,
        SET_BRIGHTNESS,
        CONTROL_WIFI,
        CONTROL_BLUETOOTH,
        SET_VOLUME,
        ENABLE_DND,
        SET_ALARM,
        UNKNOWN
    }

    data class IntentParsed(
        val type: ActionType,
        val target: String? = null,
        val content: String? = null,
        val timeOrDate: String? = null
    )

    suspend fun execute(intent: IntentParsed): Boolean {
        return try {
            when (intent.type) {
                ActionType.OPEN_APP -> executeOpenApp(intent.target ?: return false)
                ActionType.SET_REMINDER -> executeSetReminder(intent.timeOrDate ?: return false, intent.content)
                ActionType.SEND_MESSAGE -> executeSendMessage(intent.target ?: return false, intent.content ?: return false)
                ActionType.SEARCH_WEB -> executeSearchWeb(intent.content ?: return false)
                ActionType.SET_BRIGHTNESS -> executeSetBrightness(intent.content?.toIntOrNull() ?: 50)
                ActionType.CONTROL_WIFI -> executeControlWifi(intent.content == "on")
                ActionType.CONTROL_BLUETOOTH -> executeControlBluetooth(intent.content == "on")
                ActionType.SET_VOLUME -> executeSetVolume(intent.content?.toIntOrNull() ?: 50)
                ActionType.ENABLE_DND -> executeEnableDND(intent.content == "on")
                ActionType.SET_ALARM -> executeSetAlarm(intent.timeOrDate ?: return false, intent.content ?: "")
                ActionType.UNKNOWN -> {
                    Log.w(TAG, "Unknown action type")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed", e)
            false
        }
    }

    private fun executeOpenApp(appName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(appName.lowercase())
            if (intent != null) {
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            } else {
                Log.w(TAG, "App not found: $appName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $appName", e)
            false
        }
    }

    private fun executeSetReminder(timeOrDate: String, content: String?): Boolean {
        // Stub implementation for setting reminders
        Log.d(TAG, "Setting reminder for $timeOrDate: $content")
        return true
    }

    private fun executeSendMessage(target: String, content: String): Boolean {
        // Stub implementation for sending messages
        Log.d(TAG, "Sending message to $target: $content")
        return true
    }

    private fun executeSearchWeb(query: String): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search web: $query", e)
            false
        }
    }

    private fun executeSetBrightness(value: Int): Boolean {
        Log.d(TAG, "Setting brightness to: $value")
        // Real implementation requires android.permission.WRITE_SETTINGS
        return true
    }

    private fun executeControlWifi(enable: Boolean): Boolean {
        Log.d(TAG, "Setting WiFi to: $enable")
        return true
    }

    private fun executeControlBluetooth(enable: Boolean): Boolean {
        Log.d(TAG, "Setting Bluetooth to: $enable")
        return true
    }

    private fun executeSetVolume(value: Int): Boolean {
        Log.d(TAG, "Setting volume to: $value")
        return true
    }

    private fun executeEnableDND(enable: Boolean): Boolean {
        Log.d(TAG, "Setting DND to: $enable")
        return true
    }

    private fun executeSetAlarm(time: String, label: String): Boolean {
        Log.d(TAG, "Setting alarm for $time with label: $label")
        return true
    }

    companion object {
        private const val TAG = "ActionEngine"
    }
}
