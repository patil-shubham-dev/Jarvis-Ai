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

class JarvisOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var borderView: View? = null
    private var visualizerView: com.jarvisai.app.ui.views.ExecutionVisualizerView? = null
    private var timelineView: View? = null
    private var isTaskRunning = false
    private var currentOrbState = OrbState.IDLE

    enum class OrbState {
        IDLE, LISTENING, THINKING, ANALYZING, EXECUTING, ERROR
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
        
        try {
            showOverlay()
        } catch (e: Exception) {
            android.util.Log.e("JarvisOverlay", "Failed to show overlay: ${e.message}")
            stopSelf()
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return // Already showing

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        launchJarvis()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY - (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (e: Exception) { /* Ignore */ }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            overlayView = null
        }
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

    fun setOrbState(state: OrbState) {
        currentOrbState = state
        val orbGlow = overlayView?.findViewById<View>(R.id.orb_glow) ?: return
        val orbCore = overlayView?.findViewById<View>(R.id.orb_core) ?: return

        orbGlow.clearAnimation()
        
        when (state) {
            OrbState.IDLE -> {
                orbGlow.alpha = 0.6f
            }
            OrbState.LISTENING -> {
                startBreathingAnimation(orbGlow)
            }
            OrbState.THINKING -> {
                startPulseAnimation(orbGlow, 1.2f)
                showBorder(android.graphics.Color.parseColor("#BB86FC")) // Purple for thinking
            }
            OrbState.ANALYZING -> {
                startPulseAnimation(orbGlow, 1.3f)
                showBorder(android.graphics.Color.parseColor("#03DAC5")) // Teal for vision
            }
            OrbState.EXECUTING -> {
                startPulseAnimation(orbGlow, 1.4f)
                showBorder(android.graphics.Color.parseColor("#00D4FF")) // Blue for execution
            }
            OrbState.ERROR -> {
                orbGlow.setBackgroundColor(android.graphics.Color.RED)
                startPulseAnimation(orbGlow, 1.1f)
            }
        }
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
            } catch (e: Exception) { /* Ignore */ }
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
     * Updates the status message shown on the overlay bubble.
     */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
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
            } catch (e: Exception) { /* Ignore */ }
        }
        timelineView = null
    }

    override fun onDestroy() {
        overlayView?.let { 
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { /* Ignore */ }
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
        var instance: JarvisOverlayService? = null
    }
}
