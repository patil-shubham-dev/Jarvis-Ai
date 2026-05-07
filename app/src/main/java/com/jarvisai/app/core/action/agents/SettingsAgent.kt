package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import kotlinx.coroutines.delay

object SettingsAgent {
    private const val TAG = "SettingsAgent"

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
