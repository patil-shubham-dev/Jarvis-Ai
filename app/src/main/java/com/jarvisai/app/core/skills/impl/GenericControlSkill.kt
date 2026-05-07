package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.content.Intent
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult

/**
 * Atomic control skill for Sentinel V4.
 * Handles click, type, scroll, and open_app.
 */
class GenericControlSkill(
    context: Context,
    accessibility: AccessibilityHelper
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val tool = params["tool_action"] as? String ?: return SkillResult(false, "No action specified")

        return when (tool) {
            "open_app" -> openApp(params["package_name"] as? String)
            "click_element" -> clickElement(params["query"] as? String)
            "type_text" -> typeText(params["query"] as? String, params["text"] as? String)
            "scroll" -> scroll(params["direction"] as? String)
            else -> SkillResult(false, "Unknown action: $tool")
        }
    }

    private fun openApp(pkg: String?): SkillResult {
        if (pkg == null) return SkillResult(false, "No package name")
        updateStatus("Opening $pkg")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return SkillResult(false, "App not found", errorType = ErrorType.APP_NOT_FOUND)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return SkillResult(true, "App opened: $pkg")
    }

    private fun clickElement(query: String?): SkillResult {
        if (query == null) return SkillResult(false, "No element query")
        updateStatus("Clicking $query")
        val success = accessibility.performActionClick(query)
        return if (success) SkillResult(true, "Clicked $query")
        else SkillResult(false, "Could not find or click $query", errorType = ErrorType.SELECTOR_NOT_FOUND)
    }

    private fun typeText(query: String?, text: String?): SkillResult {
        if (query == null || text == null) return SkillResult(false, "Missing query or text")
        updateStatus("Typing into $query")
        val node = accessibility.findNode(query) 
            ?: return SkillResult(false, "Could not find $query", errorType = ErrorType.SELECTOR_NOT_FOUND)
        
        val success = accessibility.typeText(node, text)
        return if (success) SkillResult(true, "Typed '$text' into $query")
        else SkillResult(false, "Failed to type into $query")
    }

    private fun scroll(direction: String?): SkillResult {
        updateStatus("Scrolling $direction")
        // Simplified swipe for scroll
        val success = when (direction?.lowercase()) {
            "down" -> accessibility.performSwipe(500f, 1500f, 500f, 500f)
            "up" -> accessibility.performSwipe(500f, 500f, 500f, 1500f)
            else -> accessibility.performSwipe(500f, 1500f, 500f, 500f)
        }
        return if (success) SkillResult(true, "Scrolled $direction")
        else SkillResult(false, "Scroll failed")
    }

    override fun getDefinition(): String {
        return "generic_control(tool_action, query?, text?, package_name?, direction?): Execute system commands like 'open_app', 'click_element', 'type_text', 'scroll'."
    }

    override suspend fun verifyState(): Boolean = true
}
