package com.jarvisai.app.api.context

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the current UI state of the device.
 * Used by the recursive planner to maintain context across app transitions.
 */
@Singleton
class ScreenStateEngine @Inject constructor() {

    data class ScreenState(
        val currentPackage: String = "",
        val currentActivity: String = "",
        val screenTitle: String = "",
        val focusedElementId: String? = null,
        val isKeyboardOpen: Boolean = false,
        val lastUpdate: Long = System.currentTimeMillis()
    )

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state

    fun updateState(
        packageName: String? = null,
        activityName: String? = null,
        title: String? = null,
        focusedId: String? = null,
        keyboardOpen: Boolean? = null
    ) {
        val current = _state.value
        _state.value = current.copy(
            currentPackage = packageName ?: current.currentPackage,
            currentActivity = activityName ?: current.currentActivity,
            screenTitle = title ?: current.screenTitle,
            focusedElementId = focusedId ?: current.focusedElementId,
            isKeyboardOpen = keyboardOpen ?: current.isKeyboardOpen,
            lastUpdate = System.currentTimeMillis()
        )
        Log.d("ScreenStateEngine", "State Updated: ${_state.value.currentPackage}")
    }

    fun getCurrentContextSummary(): String {
        val s = _state.value
        return """
            CURRENT UI STATE:
            - App: ${s.currentPackage}
            - Screen: ${s.screenTitle}
            - Activity: ${s.currentActivity}
            - Focused Element: ${s.focusedElementId ?: "None"}
            - Keyboard: ${if (s.isKeyboardOpen) "Open" else "Closed"}
        """.trimIndent()
    }
}
