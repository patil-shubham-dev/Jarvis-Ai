package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay

object CalendarAgent {
    private const val TAG = "CalendarAgent"

    suspend fun addEvent(title: String, dateTime: String): Boolean {
        val accessibility = JarvisAccessibilityService.instance ?: return false
        val overlay = JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Opening Calendar...")
        
        try {
            delay(2000) 

            overlay?.updateStatus("Creating Event...")
            val createSelectors = listOf("Create new event and more", "com.google.android.calendar:id/floating_action_button")
            accessibility.performActionClick(createSelectors[0])
            delay(1000)
            
            accessibility.performActionClick("Event")
            delay(1500)

            overlay?.updateStatus("Setting Title...")
            val titleSelectors = listOf("Enter title", "com.google.android.calendar:id/title_edit_text")
            accessibility.findNode(titleSelectors[0])?.let { accessibility.typeText(it, title) }
            delay(1000)

            // Simplification: We assume the user wants to set it for the default time or we'd need more complex UI navigation for date/time pickers
            
            overlay?.updateStatus("Saving...")
            val saveSelectors = listOf("Save", "com.google.android.calendar:id/save")
            accessibility.performActionClick(saveSelectors[0])
            delay(2000)

            overlay?.updateStatus("Event Added")
            delay(1000)
            overlay?.updateStatus("")
            
            accessibility.returnToJarvis()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Calendar automation failed", e)
            overlay?.updateStatus("Failed")
            delay(1500)
            overlay?.updateStatus("")
            return false
        } finally {
            overlay?.setExecutionMode(false)
        }
    }
}
