package com.jarvisai.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvisai.app.api.context.ContextEngine
import com.jarvisai.app.api.context.ScreenStateEngine
import com.jarvisai.app.core.visual.VisualEngine
import com.jarvisai.app.core.skills.SkillManager
import com.jarvisai.app.ui.activities.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * JarvisAccessibilityService: The "Eyes and Hands" of the AI.
 * Enables screen context awareness and automated UI interactions.
 */
@AndroidEntryPoint
class JarvisAccessibilityService : AccessibilityService(), com.jarvisai.app.core.action.AccessibilityHelper {

    @Inject
    lateinit var contextEngine: ContextEngine

    @Inject
    lateinit var visualEngine: VisualEngine

    @Inject
    lateinit var skillManager: SkillManager

    @Inject
    lateinit var screenStateEngine: ScreenStateEngine

    companion object {
        private const val TAG = "JarvisAccessibility"
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Service Connected")
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
        
        skillManager.initialize(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: ""
                contextEngine.updateForegroundApp(pkg)
                screenStateEngine.updateState(packageName = pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, 
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // We don't have a markDirty but updateState triggers a flow update
                screenStateEngine.updateState()
            }
        }
    }

    // ══════════════ ACTIONS & GESTURES ══════════════

    private suspend fun performOcrClick(text: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bitmap = captureScreenshot() ?: return false
            val point = visualEngine.findTextCoordinates(bitmap, text)
            if (point != null) {
                return performTap(point.x.toFloat(), point.y.toFloat())
            }
        }
        return false
    }

    /**
     * Human-like typing with variable delays
     */
    suspend fun typeTextHumanLike(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle()
        val currentText = StringBuilder()
        
        for (char in text) {
            currentText.append(char)
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText.toString())
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Random delay between 40ms and 150ms
            delay((40..150).random().toLong())
        }
        return true
    }

    /**
     * Smooth scrolling by interpolating steps
     */
    suspend fun performSmoothSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 600): Boolean {
        val path = Path()
        path.moveTo(startX, startY)
        
        // Quad curve for more natural movement
        val midX = (startX + endX) / 2 + (0..50).random()
        val midY = (startY + endY) / 2
        path.quadTo(midX.toFloat(), midY, endX, endY)

        JarvisOverlayService.instance?.showTrail(path)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return withContext(Dispatchers.Main) {
            dispatchGesture(gesture, null, null)
        }
    }

    /**
     * Return to Jarvis Activity
     */
    fun returnToJarvis() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
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

    // ══════════════ ACCESSIBILITY HELPER IMPLEMENTATION ══════════════

    override fun findNode(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        // Try by text
        var nodes = root.findAccessibilityNodeInfosByText(query)
        if (nodes.isNotEmpty()) return nodes[0]
        
        // Try by View ID
        nodes = root.findAccessibilityNodeInfosByViewId(query)
        if (nodes.isNotEmpty()) return nodes[0]
        
        return null
    }

    override fun performActionClick(query: String): Boolean {
        val node = findNode(query) ?: return false
        var target = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    override fun typeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override suspend fun captureScreenshot(): android.graphics.Bitmap? = suspendCancellableCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )
                    cont.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed: $errorCode")
                    cont.resume(null)
                }
            })
        } else {
            cont.resume(null)
        }
    }

    override fun performTap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun getScreenContent(): String {
        val root = rootInActiveWindow ?: return "Empty Screen"
        val sb = StringBuilder()
        traverseNode(root, sb, 0)
        return sb.toString()
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return
        
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.isNotBlank()) {
            repeat(depth) { sb.append("  ") }
            sb.append("- [${node.className}] $text (ID: ${node.viewIdResourceName ?: "none"})\n")
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), sb, depth + 1)
        }
    }
}
