package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay

object SpotifyAgent {
    private const val TAG = "SpotifyAgent"

    suspend fun playMusic(query: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val overlay = JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Opening Spotify...")
        
        Log.d(TAG, "Starting Spotify autonomous playback for: $query")
        
        try {
            // 1. Wait for app to load
            delay(3000)
            
            // 2. Click Search Tab
            overlay?.updateStatus("Searching...")
            if (!service.performActionClick("Search")) {
                Log.e(TAG, "Could not find Search tab")
                return false
            }
            delay(1000)
            
            // 3. Click Search input
            if (!service.performActionClick("What do you want to listen to?")) {
                if (!service.performActionClick("Artists, songs, or podcasts")) {
                    Log.e(TAG, "Could not find Search input field")
                }
            }
            delay(1000)
            
            // 4. Type query
            overlay?.updateStatus("Typing '$query'...")
            val searchNode = service.findNode("Artists, songs, or podcasts") ?: service.findNode("Search")
            if (searchNode != null) {
                service.typeTextHumanLike(searchNode, query)
                searchNode.recycle()
            }
            delay(1500)
            
            // 5. Click the first result
            overlay?.updateStatus("Playing...")
            if (!service.performActionClick(query)) {
                service.performTap(500f, 600f)
            }
            delay(1000)
            
            overlay?.updateStatus("Success")
            delay(1000)
            overlay?.updateStatus("")

            // 6. Return to Jarvis
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
