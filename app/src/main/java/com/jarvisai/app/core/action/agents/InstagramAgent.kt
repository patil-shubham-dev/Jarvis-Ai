package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay

object InstagramAgent {
    private const val TAG = "InstagramAgent"

    suspend fun openReels(): Boolean {
        val accessibility = JarvisAccessibilityService.instance ?: return false
        val overlay = JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Opening Instagram...")
        try {
            delay(2000) // Wait for Instagram to load
            
            overlay?.updateStatus("Navigating to Reels...")
            // Try to find the Reels tab by text or content description
            val reelsFound = accessibility.performActionClick("Reels")
            
            if (reelsFound) {
                delay(1000)
                overlay?.updateStatus("Reels Opened")
                delay(1000)
                overlay?.updateStatus("")
                accessibility.returnToJarvis()
                return true
            } else {
                Log.w(TAG, "Reels tab not found")
                overlay?.updateStatus("Reels Not Found")
                delay(1500)
                overlay?.updateStatus("")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Instagram automation failed", e)
            overlay?.updateStatus("Failed")
            delay(1500)
            overlay?.updateStatus("")
            return false
        } finally {
            overlay?.setExecutionMode(false)
        }
    }
}
