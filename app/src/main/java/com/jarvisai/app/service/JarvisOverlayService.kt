package com.jarvisai.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import com.jarvisai.app.R
import com.jarvisai.app.notifications.JarvisNotificationManager
import com.jarvisai.app.ui.activities.MainActivity
import kotlinx.coroutines.*

import com.jarvisai.app.core.skills.SkillManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "JarvisOverlay"

@AndroidEntryPoint
class JarvisOverlayService : Service() {

    @Inject
    lateinit var skillManager: SkillManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var borderView: View? = null
    private var visualizerView: com.jarvisai.app.ui.views.ExecutionVisualizerView? = null
    private var timelineView: View? = null
    private var meetingPillView: View? = null
    private var isTaskRunning = false
    private var currentOrbState = OrbState.HIDDEN

    enum class OrbState {
        HIDDEN, IDLE, LISTENING, THINKING, ANALYZING, EXECUTING, SUCCESS, ERROR
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        JarvisNotificationManager.ensureChannels(this)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Updated to match manifest and added required property in manifest
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        
        // Overlay is hidden by default now
        skillManager.setOverlay(this)
    }

    private var hideRunnable: Runnable? = null

    private fun showOverlay() {
        if (overlayView != null) {
            overlayView?.animate()?.alpha(1f)?.setDuration(300)?.start()
            resetAutoHideTimer()
            return
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 200
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            resetAutoHideTimer()
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    com.jarvisai.app.utils.HapticUtil.vibrate(this, com.jarvisai.app.utils.HapticUtil.Pattern.LIGHT)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        com.jarvisai.app.utils.HapticUtil.vibrate(this, com.jarvisai.app.utils.HapticUtil.Pattern.MEDIUM)
                        launchJarvis()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Overlay move failed", e)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
            resetAutoHideTimer()
        } catch (e: Exception) {
            overlayView = null
        }
    }

    private fun resetAutoHideTimer() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        
        // Don't hide if task is running or Jarvis is talking/listening
        if (isTaskRunning || currentOrbState == OrbState.LISTENING || currentOrbState == OrbState.THINKING || currentOrbState == OrbState.ANALYZING || currentOrbState == OrbState.EXECUTING) return

        hideRunnable = Runnable {
            overlayView?.animate()?.alpha(0f)?.setDuration(1000)?.withEndAction {
                hideOverlay()
            }?.start()
        }
        handler.postDelayed(hideRunnable!!, 5000)
    }

    /**
     * Shows a pulsing blue border around the screen to indicate JARVIS is performing a task.
     */
    fun setExecutionMode(active: Boolean) {
        isTaskRunning = active
        handler.post {
            if (active) {
                setOrbState(OrbState.EXECUTING)
                showBorder(android.graphics.Color.parseColor("#00D4FF"))
                overlayView?.alpha = 0.5f // Fade to be less intrusive
            } else {
                setOrbState(OrbState.IDLE)
                hideBorder()
                hideTimeline()
                overlayView?.alpha = 1.0f
            }
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun setOrbState(state: OrbState) {
        currentOrbState = state
        handler.post {
            val currentOverlay = overlayView
            if (state == OrbState.HIDDEN) {
                hideOverlay()
                return@post
            }
            
            if (currentOverlay == null) {
                showOverlay()
            }
            
            val orbView = overlayView?.findViewById<com.jarvisai.app.ui.views.JarvisOrbView>(R.id.jarvis_orb) ?: return@post
            orbView.setState(state)
            
            when (state) {
                OrbState.IDLE -> { }
                OrbState.LISTENING -> {
                    com.jarvisai.app.utils.HapticUtil.vibrate(this, com.jarvisai.app.utils.HapticUtil.Pattern.LIGHT)
                }
                OrbState.THINKING -> {
                    showBorder(android.graphics.Color.parseColor("#BB86FC"))
                }
                OrbState.ANALYZING -> {
                    showBorder(android.graphics.Color.parseColor("#03DAC5"))
                }
                OrbState.EXECUTING -> {
                    showBorder(android.graphics.Color.parseColor("#00D4FF"))
                }
                OrbState.SUCCESS -> {
                    com.jarvisai.app.utils.HapticUtil.vibrate(this, com.jarvisai.app.utils.HapticUtil.Pattern.SUCCESS)
                    handler.postDelayed({ hideOverlay() }, 2000)
                }
                OrbState.ERROR -> {
                    com.jarvisai.app.utils.HapticUtil.vibrate(this, com.jarvisai.app.utils.HapticUtil.Pattern.ERROR)
                }
                OrbState.HIDDEN -> {
                    hideOverlay()
                }
            }
            resetAutoHideTimer()
        }
    }

    private fun hideOverlay() {
        overlayView?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
                    try {
                        windowManager?.removeView(overlayView)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to remove overlay", e)
                    }
            overlayView = null
        }?.start()
        hideBorder()
        hideTimeline()
    }

    private fun startBreathingAnimation(view: View) {
        val anim = AlphaAnimation(0.4f, 1.0f).apply {
            duration = 1500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        view.startAnimation(anim)
    }

    private fun startPulseAnimation(view: View, scale: Float) {
        val scaleAnim = ScaleAnimation(
            1.0f, scale, 1.0f, scale,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        val alphaAnim = AlphaAnimation(0.6f, 1.0f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        val set = AnimationSet(true).apply {
            addAnimation(scaleAnim)
            addAnimation(alphaAnim)
        }
        view.startAnimation(set)
    }

    private fun showBorder(color: Int = android.graphics.Color.parseColor("#00D4FF")) {
        if (borderView != null) {
            borderView?.background?.setTint(color)
            return
        }
        
        val container = android.widget.FrameLayout(this)
        
        borderView = View(this).apply {
            setBackgroundResource(R.drawable.bg_execution_border)
            background.setTint(color)
        }
        
        visualizerView = com.jarvisai.app.ui.views.ExecutionVisualizerView(this)
        
        container.addView(borderView)
        container.addView(visualizerView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(container, params)
            borderContainer = container
        } catch (e: Exception) {
            borderView = null
            visualizerView = null
        }
    }

    private var borderContainer: View? = null

    private fun hideBorder() {
        borderContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove border", e)
            }
        }
        borderContainer = null
        borderView = null
        visualizerView = null
    }

    fun showTap(x: Float, y: Float) {
        visualizerView?.addTap(x, y)
    }

    fun showTrail(path: android.graphics.Path) {
        visualizerView?.addTrail(path)
    }

    /**
     * Proactive Meeting Intelligence: Shows a pill when a meeting is detected.
     */
    fun showMeetingPill(active: Boolean) {
        handler.post {
            if (active) {
                if (meetingPillView != null) return@post
                
                meetingPillView = LayoutInflater.from(this).inflate(R.layout.layout_proactive_pill, null)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 60
                }
                
                meetingPillView?.findViewById<TextView>(R.id.pill_text)?.text = "Start Meeting Memo?"
                meetingPillView?.setOnClickListener {
                    startMeetingMemo()
                }
                
                try {
                    windowManager?.addView(meetingPillView, params)
                    meetingPillView?.alpha = 0f
                    meetingPillView?.animate()?.alpha(1f)?.setDuration(500)?.start()
                } catch (e: Exception) {
                    meetingPillView = null
                }
            } else {
                meetingPillView?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
                    try {
                        windowManager?.removeView(meetingPillView)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to remove meeting pill", e)
                    }
                    meetingPillView = null
                }?.start()
            }
        }
    }

    private fun startMeetingMemo() {
        showMeetingPill(false)
        updateStatus("Initializing Meeting Memo...")
        // Call Skill via SkillManager
        serviceScope.launch {
            skillManager.runSkill("meeting_memo", emptyMap())
        }
    }
    private var typingRunnable: Runnable? = null

    fun updateStatus(message: String) {
        handler.post {
            val currentOverlay = overlayView ?: return@post
            val bubble = currentOverlay.findViewById<View>(R.id.status_bubble) ?: return@post
            val textView = currentOverlay.findViewById<TextView>(R.id.status_text) ?: return@post
            
            if (message.isBlank()) {
                bubble.animate().alpha(0f).setDuration(300).withEndAction { 
                    bubble.visibility = View.GONE 
                }.start()
                return@post
            }

            bubble.visibility = View.VISIBLE
            bubble.alpha = 1f
            
            // Clear previous typing effect
            typingRunnable?.let { handler.removeCallbacks(it) }
            
            textView.text = ""
            var charIndex = 0
            
            typingRunnable = object : Runnable {
                override fun run() {
                    if (charIndex <= message.length) {
                        textView.text = message.substring(0, charIndex)
                        charIndex++
                        handler.postDelayed(this, 20)
                    }
                }
            }
            handler.post(typingRunnable!!)
        }
    }

    private fun launchJarvis() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    data class TimelineStep(val title: String, var status: StepStatus)
    enum class StepStatus { PENDING, RUNNING, COMPLETED, FAILED }

    fun updateTimeline(steps: List<TimelineStep>) {
        if (timelineView == null) showTimeline()
        
        val container = timelineView?.findViewById<android.widget.LinearLayout>(R.id.timeline_container) ?: return
        
        // Clear existing dynamic items (keep header)
        while (container.childCount > 1) {
            container.removeViewAt(1)
        }

        for (step in steps) {
            val itemView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
            val textView = itemView.findViewById<TextView>(android.R.id.text1)
            
            val icon = when (step.status) {
                StepStatus.PENDING -> "○"
                StepStatus.RUNNING -> "⟳"
                StepStatus.COMPLETED -> "✔"
                StepStatus.FAILED -> "✘"
            }
            
            textView.text = "$icon ${step.title}"
            textView.setTextColor(if (step.status == StepStatus.COMPLETED) android.graphics.Color.GREEN else android.graphics.Color.WHITE)
            textView.textSize = 12f
            textView.setPadding(0, 4, 0, 4)
            
            container.addView(itemView)
        }
    }

    private fun showTimeline() {
        if (timelineView != null) return
        
        timelineView = LayoutInflater.from(this).inflate(R.layout.execution_timeline, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 100
        }
        
        try {
            windowManager?.addView(timelineView, params)
        } catch (e: Exception) {
            timelineView = null
        }
    }

    private fun hideTimeline() {
        timelineView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove timeline", e)
            }
        }
        timelineView = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { 
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay in onDestroy", e)
            }
        }
        hideBorder()
        hideTimeline()
        instance = null
        super.onDestroy()
    }

    private fun buildNotification() = JarvisNotificationManager.buildServiceNotification(
        context = this,
        title = "Jarvis Active",
        text = "Ready to assist you on-device."
    )

    companion object {
        private const val NOTIF_ID = 1001
        @Volatile
        var instance: JarvisOverlayService? = null
    }
}
