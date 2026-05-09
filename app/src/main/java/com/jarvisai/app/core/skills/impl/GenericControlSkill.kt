package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.content.Intent
import android.util.Log
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
        // Fallback to skill name if tool_action is missing (handles aliases like 'open_app')
        val tool = (params["tool_action"] as? String) ?: currentSkillName ?: ""
        
        Log.d("GenericControl", "Executing tool: $tool with params: $params")

        return when (tool) {
            "open_app" -> openApp(params["package_name"] as? String)
            "click_element" -> clickElement(params["query"] as? String)
            "type_text" -> typeText(params["query"] as? String, params["text"] as? String)
            "scroll" -> scroll(params["direction"] as? String)
            "tap_at" -> tapAt(params["x"] as? Number, params["y"] as? Number)
            "swipe_at" -> swipeAt(params["x"] as? Number, params["y"] as? Number, params["x2"] as? Number, params["y2"] as? Number)
            "type_at" -> typeAt(params["content"] as? String, params["x"] as? Number, params["y"] as? Number)
            else -> SkillResult(false, "Unknown action: $tool")
        }
    }

    private fun tapAt(x: Number?, y: Number?): SkillResult {
        if (x == null || y == null) return SkillResult(false, "Coordinates missing")
        val success = accessibility.performTap(x.toFloat(), y.toFloat())
        return if (success) SkillResult(true, "Tapped at [$x, $y]")
        else SkillResult(false, "Tap failed")
    }

    private fun swipeAt(x: Number?, y: Number?, x2: Number?, y2: Number?): SkillResult {
        if (x == null || y == null || x2 == null || y2 == null) return SkillResult(false, "Coordinates missing")
        val success = accessibility.performSwipe(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), 500)
        return if (success) SkillResult(true, "Swiped from [$x, $y] to [$x2, $y2]")
        else SkillResult(false, "Swipe failed")
    }

    private suspend fun typeAt(content: String?, x: Number?, y: Number?): SkillResult {
        if (content == null) return SkillResult(false, "No text provided")
        if (x != null && y != null) {
            accessibility.performTap(x.toFloat(), y.toFloat())
            kotlinx.coroutines.delay(300)
        }
        
        val root = accessibility.findNode("focused") ?: (accessibility as? com.jarvisai.app.service.JarvisAccessibilityService)?.rootInActiveWindow
        val focused = root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        
        return if (focused != null) {
            val success = accessibility.typeText(focused, content)
            if (success) SkillResult(true, "Typed '$content'")
            else SkillResult(false, "Type failed")
        } else {
            SkillResult(false, "No input field focused")
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

    private suspend fun clickElement(query: String?): SkillResult {
        if (query == null) return SkillResult(false, "No element query")
        updateStatus("Clicking $query")
        val success = accessibility.performHybridClick(query)
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
        return """
            generic_control(tool_action, query?, text?, content?, x?, y?, x2?, y2?, package_name?, direction?): 
            Actions: 
            - 'open_app' (package_name)
            - 'click_element' (query: text or ID)
            - 'type_text' (query, text)
            - 'scroll' (direction: up/down)
            - 'tap_at' (x, y)
            - 'swipe_at' (x, y, x2, y2)
            - 'type_at' (content, x?, y?)
        """.trimIndent()
    }

    override suspend fun verifyState(): Boolean = true
}
