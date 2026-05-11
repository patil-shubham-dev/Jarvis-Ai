package com.jarvisai.app.core.action

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Interface to decouple Skill execution from the AccessibilityService implementation.
 */
interface AccessibilityHelper {
    fun findNode(query: String): AccessibilityNodeInfo?
    fun performActionClick(query: String): Boolean
    suspend fun performHybridClick(query: String): Boolean
    fun typeText(node: AccessibilityNodeInfo, text: String): Boolean
    suspend fun captureScreenshot(): android.graphics.Bitmap?
    fun performTap(x: Float, y: Float): Boolean
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean
    fun getScreenContent(): String
    fun getScreenNodes(): List<com.jarvisai.app.api.context.ScreenStateEngine.NodeSummary>
}
