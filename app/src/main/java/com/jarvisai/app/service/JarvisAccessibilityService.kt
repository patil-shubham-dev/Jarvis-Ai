package com.jarvisai.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvisai.app.api.context.ContextEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * JarvisAccessibilityService: The "Eyes and Hands" of the AI.
 * Enables screen context awareness and automated UI interactions.
 */
@AndroidEntryPoint
class JarvisAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var contextEngine: ContextEngine

    companion object {
        private const val TAG = "JarvisAccessibility"
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Jarvis Intelligence Accessibility Service Connected")
        
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // Real-time Context Extraction
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: ""
                contextEngine.updateForegroundApp(packageName)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = event.text?.joinToString(" ") ?: ""
                Log.d(TAG, "User clicked: $text")
            }
        }
    }

    /**
     * Professional Screen Content Extraction for AI Context
     */
    fun getScreenContent(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        extractText(rootNode, sb)
        return sb.toString()
    }

    private fun extractText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { 
            if (it.isNotBlank()) sb.append(it).append("\n") 
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractText(child, sb)
                child.recycle()
            }
        }
    }

    /**
     * Automated Interaction: Perform a click on a view with specific text
     */
    fun performActionClick(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                if (success) return true
            }
            node.recycle()
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
