package com.jarvisai.app.core.action.agents

import android.util.Log
import com.jarvisai.app.service.JarvisAccessibilityService
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay

object EmailAgent {
    private const val TAG = "EmailAgent"

    suspend fun sendEmail(recipient: String, subject: String, body: String): Boolean {
        val accessibility = JarvisAccessibilityService.instance ?: return false
        val overlay = JarvisOverlayService.instance

        overlay?.setExecutionMode(true)
        overlay?.updateStatus("Opening Gmail...")
        
        try {
            delay(2000) 

            overlay?.updateStatus("Composing...")
            val composeSelectors = listOf("Compose", "com.google.android.gm:id/compose_button")
            if (!accessibility.performActionClick(composeSelectors[0])) {
                accessibility.performActionClick(composeSelectors[1])
            }
            delay(1500)

            overlay?.updateStatus("Setting Recipient...")
            val toSelectors = listOf("To", "com.google.android.gm:id/to")
            accessibility.findNode(toSelectors[0])?.let { accessibility.typeText(it, recipient) }
            delay(1000)

            overlay?.updateStatus("Setting Subject...")
            val subjectSelectors = listOf("Subject", "com.google.android.gm:id/subject")
            accessibility.findNode(subjectSelectors[0])?.let { accessibility.typeText(it, subject) }
            delay(1000)

            overlay?.updateStatus("Writing Body...")
            val bodySelectors = listOf("Compose email", "com.google.android.gm:id/body")
            accessibility.findNode(bodySelectors[0])?.let { accessibility.typeText(it, body) }
            delay(1500)

            overlay?.updateStatus("Sending...")
            val sendSelectors = listOf("Send", "com.google.android.gm:id/send")
            accessibility.performActionClick(sendSelectors[0])
            delay(2000)

            overlay?.updateStatus("Email Sent")
            delay(1000)
            overlay?.updateStatus("")
            
            accessibility.returnToJarvis()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Email automation failed", e)
            overlay?.updateStatus("Failed")
            delay(1500)
            overlay?.updateStatus("")
            return false
        } finally {
            overlay?.setExecutionMode(false)
        }
    }
}
