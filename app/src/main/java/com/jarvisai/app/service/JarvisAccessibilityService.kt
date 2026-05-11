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
import com.jarvisai.app.api.vision.LocalVisionEngine
import com.jarvisai.app.api.context.ScreenStateEngine
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
    lateinit var localVisionEngine: LocalVisionEngine

    @Inject
    lateinit var skillManager: SkillManager

    @Inject
    lateinit var screenStateEngine: ScreenStateEngine

    @Inject
    lateinit var habitRepository: com.jarvisai.app.data.repository.HabitRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mainExecutor: Executor

    companion object {
        private const val TAG = "JarvisAccessibility"
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Service Connected")
        instance = this
        mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
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
                
                // Track habit
                serviceScope.launch {
                    habitRepository.logAppUsage(pkg)
                    
                    // Check for proactive suggestions
                    val suggestions = habitRepository.getRoutineSuggestions()
                    if (suggestions.contains(pkg)) {
                        Log.i(TAG, "Proactive Habit Match: $pkg")
                        // In a real app, we'd show a more specific suggestion
                    }
                }
                
                checkProactiveTriggers(pkg)
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
 
    /**
     * Hybrid Click Engine: Tries node-based click first, then falls back to OCR/Visual Tap.
     */
    override suspend fun performHybridClick(query: String): Boolean {
        Log.d(TAG, "Hybrid Click Attempt: $query")
        
        // 1. Try standard Accessibility Node click
        val nodeClickSuccess = performActionClick(query)
        if (nodeClickSuccess) {
            Log.d(TAG, "Node-based click successful for: $query")
            return verifyActionSuccess(query)
        }

        // 2. Fallback: Visual OCR Tap (MobileNet V3 + OCR)
        Log.d(TAG, "Node-based click failed, falling back to Visual Tap for: $query")
        val visualClickSuccess = performOcrClick(query)
        if (visualClickSuccess) {
            return verifyActionSuccess(query)
        }

        return false
    }

    /**
     * Autonomous Verification Loop: Confirms UI changed after action.
     */
    private suspend fun verifyActionSuccess(query: String): Boolean {
        delay(1000) // Wait for UI transition
        
        // Simple verification: Is the button still there and clickable?
        val node = findNode(query)
        val isStillThere = node != null && node.isVisibleToUser
        
        if (isStillThere) {
            Log.w(TAG, "Verification failed: $query still visible. Retrying with raw tap.")
            // Try one last raw tap if it's still there
            val rect = Rect()
            node?.getBoundsInScreen(rect)
            return performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        
        Log.d(TAG, "Action verified: $query is no longer the primary focus.")
        return true
    }

    /**
     * Proactive Contextual Awareness: Detects specific app contexts.
     */
    private fun checkProactiveTriggers(packageName: String) {
        val meetingApps = listOf("com.google.android.apps.meetings", "com.microsoft.teams", "us.zoom.videomeetings")
        if (meetingApps.contains(packageName)) {
            Log.i(TAG, "Meeting detected! Offering proactive assistance.")
            JarvisOverlayService.instance?.showMeetingPill(true)
        } else {
            // Hide if we leave the meeting app
            JarvisOverlayService.instance?.showMeetingPill(false)
        }
    }

    private suspend fun performOcrClick(text: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bitmap = captureScreenshot() ?: return false
            val point = localVisionEngine.findTextCoordinates(bitmap, text)
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
        serviceScope.cancel()
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

    override suspend fun captureScreenshot(): android.graphics.Bitmap? = withTimeoutOrNull(5000) {
        suspendCancellableCoroutine { cont ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val buffer = screenshot.hardwareBuffer
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                    buffer,
                                    screenshot.colorSpace
                                )
                                // Force a software copy if needed to ensure availability across threads
                                val result = if (bitmap != null && bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                                    bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                } else {
                                    bitmap
                                }
                                cont.resume(result)
                            } catch (e: Exception) {
                                Log.e(TAG, "Bitmap conversion failed", e)
                                cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Screenshot capture failed: $errorCode")
                            cont.resume(null)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "takeScreenshot exception", e)
                    cont.resume(null)
                }
            } else {
                cont.resume(null)
            }
        }
    }

    override fun performTap(x: Float, y: Float): Boolean {
        Log.d(TAG, "Performing tap at ($x, $y)")
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        val result = dispatchGesture(gesture, null, null)
        Log.d(TAG, "Tap result: $result")
        return result
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
        if (node == null || depth > 20) return // Reduced depth cap
        
        val pkg = node.packageName?.toString() ?: ""
        // Skip keyboard nodes as they bloat the context with irrelevant data
        if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return

        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        
        if (text.isNotBlank() && node.isVisibleToUser) {
            sb.append("- [${className.substringAfterLast('.')}] $text\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, sb, depth + 1)
                try { child.recycle() } catch (e: Exception) {}
            }
        }
    }
}
