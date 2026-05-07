package com.jarvisai.app.core.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.jarvisai.app.data.local.dao.ReminderDao
import com.jarvisai.app.data.models.ReminderEntity
import com.jarvisai.app.notifications.JarvisNotificationManager
import com.jarvisai.app.notifications.ReminderScheduler
import com.jarvisai.app.core.action.agents.InstagramAgent
import com.jarvisai.app.core.action.agents.SettingsAgent
import com.jarvisai.app.core.action.agents.SpotifyAgent
import com.jarvisai.app.core.action.agents.WhatsAppAgent
import com.jarvisai.app.core.action.agents.FileAgent
import com.jarvisai.app.core.routines.RoutineEngine
import com.jarvisai.app.service.JarvisAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler,
    private val routineEngine: dagger.Lazy<RoutineEngine> // Lazy to avoid circular dependency if any
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
        NAVIGATE,
        PLAY_MUSIC,
        ROUTINE,
        SHARE_FILE,
        VISUAL_SEARCH,
        UNKNOWN
    }

    data class IntentParsed(
        val type: ActionType,
        val target: String? = null,
        val content: String? = null,
        val timeOrDate: String? = null,
        val triggerAtMillis: Long? = null,
        val sessionId: String? = null
    )

    suspend fun execute(command: String): Boolean {
        return when (command.lowercase()) {
            "press back" -> {
                JarvisAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                true
            }
            "press home" -> {
                JarvisAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                true
            }
            else -> false
        }
    }

    suspend fun execute(intent: IntentParsed): Boolean {
        return try {
            when (intent.type) {
                ActionType.OPEN_APP -> executeOpenApp(intent.target ?: return false)
                ActionType.SET_REMINDER -> executeSetReminder(
                    triggerAtMillis = intent.triggerAtMillis ?: return false,
                    content = intent.content ?: return false,
                    sessionId = intent.sessionId
                )
                ActionType.SEND_MESSAGE -> executeSendMessage(intent.target ?: return false, intent.content ?: return false)
                ActionType.SEARCH_WEB -> executeSearchWeb(intent.content ?: return false)
                ActionType.SET_BRIGHTNESS -> executeSetBrightness(intent.content?.toIntOrNull() ?: 50)
                ActionType.CONTROL_WIFI -> executeControlWifi(intent.content == "on")
                ActionType.CONTROL_BLUETOOTH -> executeControlBluetooth(intent.content == "on")
                ActionType.SET_VOLUME -> executeSetVolume(intent.content?.toIntOrNull() ?: 50)
                ActionType.ENABLE_DND -> executeEnableDND(intent.content == "on")
                ActionType.SET_ALARM -> executeSetAlarm(
                    triggerAtMillis = intent.triggerAtMillis ?: return false,
                    label = intent.content ?: "Jarvis Alarm"
                )
                ActionType.NAVIGATE -> executeNavigate(intent.target ?: "", intent.content ?: "")
                ActionType.PLAY_MUSIC -> executePlayMusic(intent.content ?: return false)
                ActionType.ROUTINE -> routineEngine.get().executeRoutine(intent.target ?: return false)
                ActionType.SHARE_FILE -> executeShareFile(intent.target, intent.content)
                ActionType.VISUAL_SEARCH -> executeVisualSearch(intent.content ?: return false)
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
            val pm = context.packageManager
            var intent = pm.getLaunchIntentForPackage(appName)
            
            if (intent == null) {
                // Hardcoded common mappings for speed/reliability
                val packageMap = mapOf(
                    "whatsapp" to "com.whatsapp",
                    "spotify" to "com.spotify.music",
                    "instagram" to "com.instagram.android",
                    "youtube" to "com.google.android.youtube",
                    "chrome" to "com.android.chrome",
                    "maps" to "com.google.android.apps.maps",
                    "settings" to "com.android.settings"
                )
                
                val directPackage = packageMap[appName.lowercase()]
                if (directPackage != null) {
                    intent = pm.getLaunchIntentForPackage(directPackage)
                }
            }

            if (intent == null) {
                val packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                val targetPackage = packages.find { pkg ->
                    val label = pm.getApplicationLabel(pkg).toString().lowercase()
                    label == appName.lowercase() || label.contains(appName.lowercase())
                }?.packageName
                
                if (targetPackage != null) {
                    intent = pm.getLaunchIntentForPackage(targetPackage)
                }
            }

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

    private suspend fun executeSetReminder(
        triggerAtMillis: Long,
        content: String,
        sessionId: String?
    ): Boolean {
        return try {
            val reminderId = reminderDao.insert(
                ReminderEntity(
                    title = content.take(60).ifBlank { "Reminder" },
                    message = content,
                    triggerAtMillis = triggerAtMillis,
                    sessionId = sessionId,
                    notificationTag = "task"
                )
            )
            reminderScheduler.schedule(reminderId, triggerAtMillis)
            JarvisNotificationManager.showStatusNotification(
                context = context,
                notificationId = reminderId.toInt(),
                title = "Reminder Scheduled",
                message = "I'll remind you soon."
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminder", e)
            false
        }
    }

    private suspend fun executeSendMessage(target: String, content: String): Boolean {
        return try {
            if (executeOpenApp("com.whatsapp")) {
                return WhatsAppAgent.sendMessage(target, content)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp message", e)
            false
        }
    }

    private suspend fun executeNavigate(app: String, destination: String): Boolean {
        return try {
            when (app.lowercase()) {
                "instagram" -> {
                    if (executeOpenApp("com.instagram.android")) {
                        if (destination.contains("reel", ignoreCase = true)) {
                            return InstagramAgent.openReels()
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed", e)
            false
        }
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

    private suspend fun executePlayMusic(query: String): Boolean {
        return try {
            if (executeOpenApp("com.spotify.music")) {
                return SpotifyAgent.playMusic(query)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play music", e)
            false
        }
    }

    private fun executeSetBrightness(value: Int): Boolean {
        Log.d(TAG, "Setting brightness to: $value")
        return try {
            val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            // In a real flow, SettingsAgent would then take over
            true
        } catch (e: Exception) {
            false
        }
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

    private fun executeSetAlarm(triggerAtMillis: Long, label: String): Boolean {
        return try {
            val zonedDateTime = Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault())
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, zonedDateTime.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, zonedDateTime.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis Alarm" })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            false
        }
    }

    private suspend fun executeShareFile(target: String?, content: String?): Boolean {
        val file = FileAgent.findLastDownloadedFile(context, content ?: ".pdf") ?: return false
        return FileAgent.shareFile(context, file, target)
    }

    private suspend fun executeVisualSearch(query: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        return service.performActionClick(query)
    }

    companion object {
        private const val TAG = "ActionEngine"
    }
}
