package com.jarvisai.app.api.context

import android.graphics.Rect
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

    data class NodeSummary(
        val text: String,
        val contentDescription: String?,
        val resourceId: String?,
        val className: String?,
        val bounds: Rect,
        val isClickable: Boolean
    ) {
        val key: String get() = resourceId ?: contentDescription ?: text
        
        fun toPromptString(): String {
            val parts = mutableListOf<String>()
            if (resourceId != null) parts.add("id=$resourceId")
            if (contentDescription != null) parts.add("desc=$contentDescription")
            if (text.isNotBlank()) parts.add("text=\"$text\"")
            val type = className?.substringAfterLast('.') ?: "View"
            return "[$type] ${parts.joinToString(", ")} @ ${bounds.centerX()},${bounds.centerY()}${if (isClickable) " [Clickable]" else ""}"
        }
    }

    data class TreeDiff(
        val added: List<NodeSummary>,
        val removed: List<String>,
        val changed: List<NodeSummary>,
        val unchangedCount: Int
    )

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state

    private var previousTreeSnapshot: Map<String, NodeSummary> = emptyMap()

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

    fun computeDiff(currentNodes: List<NodeSummary>): TreeDiff {
        val currentMap = currentNodes.associateBy { it.key }
        
        val added = currentMap.values.filter { it.key !in previousTreeSnapshot }
        val removed = previousTreeSnapshot.keys.filter { it !in currentMap }.toList()
        val changed = currentMap.values.filter { node ->
            val prev = previousTreeSnapshot[node.key]
            prev != null && prev != node
        }
        
        val unchangedCount = currentMap.size - added.size - changed.size
        
        previousTreeSnapshot = currentMap
        
        return TreeDiff(added, removed, changed, unchangedCount)
    }

    fun TreeDiff.toPromptString(): String {
        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return "SCREEN_UNCHANGED ($unchangedCount nodes identical to previous step)"
        }
        return buildString {
            if (added.isNotEmpty()) {
                append("NEW ELEMENTS:\n")
                added.forEach { append("  + ${it.toPromptString()}\n") }
            }
            if (removed.isNotEmpty()) {
                append("REMOVED ELEMENTS: ${removed.joinToString()}\n")
            }
            if (changed.isNotEmpty()) {
                append("CHANGED ELEMENTS:\n")
                changed.forEach { append("  * ${it.toPromptString()}\n") }
            }
            append("UNCHANGED: $unchangedCount nodes")
        }
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
