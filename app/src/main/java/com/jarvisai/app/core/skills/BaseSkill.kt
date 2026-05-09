package com.jarvisai.app.core.skills

import android.content.Context
import android.util.Log
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.service.JarvisOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

/**
 * Foundation for all autonomous skills in Sentinel V2.
 * Each skill is a self-contained unit of capability (e.g., Messaging, Music, Smart Home).
 */
abstract class BaseSkill(
    protected val context: Context,
    protected val accessibility: AccessibilityHelper
) {
    protected val TAG = this::class.java.simpleName
    internal var overlay: JarvisOverlayService? = null
    internal var skillManager: SkillManager? = null
    internal var currentSkillName: String? = null

    /**
     * Executes the skill with the given parameters.
     * Returns a SkillResult indicating success/failure and any extracted data.
     */
    abstract suspend fun execute(params: Map<String, Any>): SkillResult

    /**
     * Returns a description of the skill for the LLM.
     */
    abstract fun getDefinition(): String

    /**
     * Verifies if the action was successful by checking the screen state.
     */
    abstract suspend fun verifyState(): Boolean

    /**
     * Helper to perform a sequence of actions with automatic retries and delays.
     */
    protected suspend fun <T> runWithRetry(
        maxRetries: Int = 2,
        action: suspend () -> T?
    ): T? {
        var lastException: Exception? = null
        for (i in 0..maxRetries) {
            try {
                val result = action()
                if (result != null) return result
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Retry ${i+1}/$maxRetries failed: ${e.message}")
            }
            delay(1000L * (i + 1))
        }
        return null
    }

    protected fun updateStatus(msg: String) {
        overlay?.updateStatus(msg)
        // Also pipe to chat history if it's a significant update
        CoroutineScope(Dispatchers.IO).launch {
            skillManager?.postStatus(msg)
        }
    }
}

data class SkillResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap(),
    val errorType: ErrorType? = null
)

enum class ErrorType {
    APP_NOT_FOUND,
    SELECTOR_NOT_FOUND,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    TIMEOUT,
    UNKNOWN
}
