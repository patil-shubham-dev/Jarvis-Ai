package com.jarvisai.app.core.security

import com.jarvisai.app.api.agents.TaskStep
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures autonomous actions are safe and protected.
 * Flags high-risk operations for user confirmation.
 */
@Singleton
class SafetyEngine @Inject constructor() {

    private val dangerousKeywords = listOf(
        "pay", "transfer", "buy", "purchase", "delete", "remove", "wipe", "uninstall"
    )

    private val dangerousPackages = listOf(
        "com.android.settings", "com.android.vending", "com.paypal.android", "com.binance"
    )

    fun isActionSafe(step: TaskStep): SafetyResult {
        // 1. Check tool name
        if (step.toolName == "click_element") {
            val query = (step.params?.get("query") as? String)?.lowercase() ?: ""
            if (dangerousKeywords.any { query.contains(it) }) {
                return SafetyResult(false, "This action might perform a sensitive operation (e.g., $query).")
            }
        }

        // 2. Check package name
        if (step.toolName == "open_app") {
            val pkg = (step.params?.get("package_name") as? String) ?: ""
            if (dangerousPackages.any { pkg.contains(it) }) {
                return SafetyResult(false, "Opening this app ($pkg) is restricted for autonomous control.")
            }
        }

        // 3. Financial keywords in descriptions (Null-safe)
        val description = step.description ?: ""
        if (dangerousKeywords.any { description.lowercase().contains(it) }) {
            return SafetyResult(false, "The task involves a potentially dangerous operation: $description")
        }

        return SafetyResult(true)
    }

    data class SafetyResult(
        val isSafe: Boolean,
        val reason: String? = null
    )
}
