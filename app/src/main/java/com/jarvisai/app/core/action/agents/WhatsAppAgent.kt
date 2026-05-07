package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay

object WhatsAppAgent {
    private const val TAG = "WhatsAppAgent"

    suspend fun sendMessage(contactName: String, message: String): Boolean {
        val accessibility = JarvisAccessibilityService.instance ?: return false
        val overlay = JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Opening WhatsApp...")
        
        try {
            // Wait for app to load
            delay(1500) 

            // Search for contact
            overlay?.updateStatus("Finding $contactName...")
            // WhatsApp's newer versions use "Ask Meta AI or Search" or just "Search"
            val searchSelectors = listOf("Search", "menu_search", "Ask Meta AI or Search", "Search…")
            var searchNodeFound = false
            for (selector in searchSelectors) {
                if (accessibility.findNode(selector) != null) {
                    accessibility.performActionClick(selector)
                    searchNodeFound = true
                    break
                }
            }
            
            if (!searchNodeFound) {
                // Fallback to searching by ID if text fails
                accessibility.performActionClick("com.whatsapp:id/menu_search")
            }
            delay(1000)

            // Type contact name
            val inputSelectors = listOf("Search…", "Ask Meta AI or Search", "Search")
            var inputFound = false
            for (selector in inputSelectors) {
                accessibility.findNode(selector)?.let { 
                    accessibility.typeText(it, contactName)
                    inputFound = true
                }
                if (inputFound) break
            }
            delay(1500)

            // Tap on contact result
            if (accessibility.performActionClick(contactName)) {
                delay(1000)

                // Type and send message
                overlay?.updateStatus("Typing message...")
                // Verification: ensure we are actually in a chat (look for "Message" input)
                val messageInputSelectors = listOf("Message", "Entry", "com.whatsapp:id/entry")
                var messageTyped = false
                for (selector in messageInputSelectors) {
                    accessibility.findNode(selector)?.let {
                        accessibility.typeText(it, message)
                        messageTyped = true
                    }
                    if (messageTyped) break
                }
                
                if (messageTyped) {
                    delay(800)
                    overlay?.updateStatus("Sending...")
                    val sendSelectors = listOf("Send", "send_button", "com.whatsapp:id/send")
                    for (selector in sendSelectors) {
                        if (accessibility.performActionClick(selector)) break
                    }
                    delay(1500)
                } else {
                    Log.e(TAG, "Could not find message input field")
                }
            }

            overlay?.updateStatus("Task Complete")
            delay(1000)
            overlay?.updateStatus("")
            
            // Return to Jarvis
            accessibility.returnToJarvis()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp automation failed", e)
            overlay?.updateStatus("Failed")
            delay(1500)
            overlay?.updateStatus("")
            return false
        } finally {
            overlay?.setExecutionMode(false)
        }
    }
}
