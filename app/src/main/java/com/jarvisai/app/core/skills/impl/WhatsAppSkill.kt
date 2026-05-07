package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.util.Log
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult
import kotlinx.coroutines.delay

/**
 * Robust WhatsApp messaging skill for Sentinel V2.
 * Handles contact searching, message typing, and verification.
 */
class WhatsAppSkill(
    context: Context,
    accessibility: AccessibilityHelper
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val contact = params["contact"] as? String ?: return SkillResult(false, "No contact provided", errorType = ErrorType.UNKNOWN)
        val message = params["message"] as? String ?: return SkillResult(false, "No message provided", errorType = ErrorType.UNKNOWN)

        updateStatus("Opening WhatsApp...")
        // Open WhatsApp via ActionEngine (delegated back if needed or use direct launch)
        
        // 1. Search for contact
        updateStatus("Searching: $contact")
        val searchSelectors = listOf("Search", "menu_search", "Ask Meta AI or Search", "com.whatsapp:id/menu_search")
        var foundSearch = false
        for (selector in searchSelectors) {
            if (accessibility.performActionClick(selector)) {
                foundSearch = true
                break
            }
        }

        if (!foundSearch) return SkillResult(false, "Could not find WhatsApp search bar", errorType = ErrorType.SELECTOR_NOT_FOUND)
        delay(800)

        // 2. Type name
        updateStatus("Entering name...")
        val inputSelectors = listOf("Search…", "Ask Meta AI or Search", "Search")
        var typed = false
        for (selector in inputSelectors) {
            accessibility.findNode(selector)?.let {
                accessibility.typeText(it, contact)
                typed = true
            }
            if (typed) break
        }
        delay(1500)

        // 3. Select contact
        updateStatus("Selecting $contact")
        if (!accessibility.performActionClick(contact)) {
            return SkillResult(false, "Contact '$contact' not found in search results", errorType = ErrorType.SELECTOR_NOT_FOUND)
        }
        delay(1200)

        // 4. Verify we are in the chat
        if (!verifyState()) {
            return SkillResult(false, "Failed to enter chat with $contact", errorType = ErrorType.UNKNOWN)
        }

        // 5. Type and Send
        updateStatus("Typing message...")
        val msgInput = accessibility.findNode("Message") ?: accessibility.findNode("com.whatsapp:id/entry")
        if (msgInput != null) {
            accessibility.typeText(msgInput, message)
            delay(500)
            updateStatus("Sending...")
            val sendBtn = listOf("Send", "com.whatsapp:id/send", "send_button")
            for (s in sendBtn) {
                if (accessibility.performActionClick(s)) break
            }
            delay(1000)
            return SkillResult(true, "Message sent to $contact")
        }

        return SkillResult(false, "Could not find message input field", errorType = ErrorType.SELECTOR_NOT_FOUND)
    }

    override fun getDefinition(): String {
        return "send_whatsapp(contact, message): Search for a contact and send a WhatsApp message."
    }

    override suspend fun verifyState(): Boolean {
        // We are in a chat if the 'Message' or 'Entry' field is visible
        return accessibility.findNode("Message") != null || 
               accessibility.findNode("com.whatsapp:id/entry") != null ||
               accessibility.findNode("Voice message") != null
    }
}
