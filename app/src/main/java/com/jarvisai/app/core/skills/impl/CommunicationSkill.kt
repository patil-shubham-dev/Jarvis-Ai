package com.jarvisai.app.core.skills.impl

import android.content.Context
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.action.agents.EmailAgent
import com.jarvisai.app.core.action.agents.WhatsAppAgent
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult

class CommunicationSkill(
    context: Context,
    accessibility: AccessibilityHelper
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val type = params["type"] as? String ?: "whatsapp"
        val recipient = params["recipient"] as? String ?: return SkillResult(false, "Missing recipient", errorType = ErrorType.UNKNOWN)
        val content = params["content"] as? String ?: return SkillResult(false, "Missing content", errorType = ErrorType.UNKNOWN)
        
        return when (type.lowercase()) {
            "email" -> {
                val subject = params["subject"] as? String ?: "No Subject"
                val success = EmailAgent.sendEmail(recipient, subject, content)
                SkillResult(success, if (success) "Email sent" else "Failed to send email")
            }
            "whatsapp" -> {
                val success = WhatsAppAgent.sendMessage(recipient, content)
                SkillResult(success, if (success) "WhatsApp message sent" else "Failed to send WhatsApp message")
            }
            else -> SkillResult(false, "Unknown communication type: $type", errorType = ErrorType.UNKNOWN)
        }
    }

    override fun getDefinition(): String {
        return "communication(type, recipient, content, subject?): Send messages via WhatsApp or Email. Type can be 'whatsapp' or 'email'."
    }

    override suspend fun verifyState(): Boolean = true
}
