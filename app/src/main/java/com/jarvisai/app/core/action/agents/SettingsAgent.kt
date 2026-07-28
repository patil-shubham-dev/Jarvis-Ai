package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import kotlinx.coroutines.delay

object SettingsAgent {
    private const val TAG = "SettingsAgent"

    suspend fun setTimer(duration: String): Boolean {
        Log.d(TAG, "Setting timer for: $duration")
        val service = JarvisAccessibilityService.instance ?: return false
        try {
            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(android.provider.AlarmClock.EXTRA_LENGTH, duration.filter { it.isDigit() }.toIntOrNull() ?: 60)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            com.jarvisai.app.service.JarvisOverlayService.instance?.updateStatus("Opening Clock app...")
            service.applicationContext.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            return false
        }
    }

    suspend fun setAlarm(time: String, label: String): Boolean {
        Log.d(TAG, "Setting alarm at: $time ($label)")
        val service = JarvisAccessibilityService.instance ?: return false
        try {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 7
            val minute = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.applicationContext.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            return false
        }
    }

    suspend fun setReminder(title: String, time: String): Boolean {
        Log.d(TAG, "Setting reminder: $title at $time")
        val service = JarvisAccessibilityService.instance ?: return false
        try {
            com.jarvisai.app.service.JarvisOverlayService.instance?.updateStatus("Setting reminder...")
            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, 9)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, 0)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "[Reminder] $title")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.applicationContext.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set reminder", e)
            return false
        }
    }

    suspend fun toggleSetting(settingName: String, enable: Boolean): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val overlay = com.jarvisai.app.service.JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Navigating Settings...")
        
        Log.d(TAG, "Navigating settings for: $settingName -> $enable")
        
        try {
            // 1. Wait for settings to load
            delay(1500)
            
            // 2. Find the setting node
            overlay?.updateStatus("Finding $settingName...")
            val node = service.findNode(settingName)
            if (node != null) {
                // Check if it's a switch
                if (node.isCheckable) {
                    if (node.isChecked != enable) {
                        overlay?.updateStatus("Toggling...")
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    }
                } else {
                    service.performActionClick(settingName)
                }
                node.recycle()
            } else {
                overlay?.updateStatus("Searching settings...")
                service.performActionClick("Search settings")
                delay(500)
                val searchBar = service.findNode("Search settings")
                if (searchBar != null) {
                    service.typeText(searchBar, settingName)
                    searchBar.recycle()
                    delay(1000)
                    service.performTap(300f, 400f) // Tap first result
                }
            }
            
            overlay?.updateStatus("Setting Updated")
            delay(1000)
            overlay?.updateStatus("")
            service.returnToJarvis()
            return true
        } catch (e: Exception) {
            overlay?.updateStatus("Failed")
            delay(1500)
            overlay?.updateStatus("")
            return false
        } finally {
            overlay?.setExecutionMode(false)
        }
    }
}
