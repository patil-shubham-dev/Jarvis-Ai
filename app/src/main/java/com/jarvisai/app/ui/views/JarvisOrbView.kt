package com.jarvisai.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.jarvisai.app.service.JarvisOverlayService.OrbState
import kotlin.math.sin
import kotlin.math.PI

class JarvisOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentState = OrbState.IDLE
    private var pulseValue = 0f
    private var rotationValue = 0f

    private val idleColor = context.getColor(com.jarvisai.app.R.color.jarvis_accent)
    private val thinkingColor = context.getColor(com.jarvisai.app.R.color.jarvis_primary)
    private val analyzingColor = context.getColor(com.jarvisai.app.R.color.jarvis_accent)
    private val executingColor = context.getColor(com.jarvisai.app.R.color.jarvis_primary)
    private val successColor = context.getColor(com.jarvisai.app.R.color.status_success)
    private val errorColor = context.getColor(com.jarvisai.app.R.color.status_error)

    private var targetColor = idleColor
    private var currentColor = idleColor
    private var colorAnimator: ValueAnimator? = null

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulseValue = it.animatedValue as Float
            rotationValue += 2f
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
        colorAnimator?.cancel()
    }

    fun setState(state: OrbState) {
        if (currentState == state) return
        currentState = state

        targetColor = when (state) {
            OrbState.IDLE -> idleColor
            OrbState.LISTENING -> idleColor
            OrbState.THINKING -> thinkingColor
            OrbState.ANALYZING -> analyzingColor
            OrbState.EXECUTING -> executingColor
            OrbState.SUCCESS -> successColor
            OrbState.ERROR -> errorColor
        }

        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofArgb(currentColor, targetColor).apply {
            duration = 500
            addUpdateListener {
                currentColor = it.animatedValue as Int
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = width * 0.35f

        val glowRadius = baseRadius * (1.2f + 0.2f * sin(pulseValue * PI.toFloat()).toFloat())
        val glowGradient = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(adjustAlpha(currentColor, 0.3f), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = glowGradient
        canvas.drawCircle(centerX, centerY, glowRadius, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = adjustAlpha(currentColor, 0.8f)
        val ringRect = RectF(
            centerX - baseRadius * 0.9f, centerY - baseRadius * 0.9f,
            centerX + baseRadius * 0.9f, centerY + baseRadius * 0.9f
        )
        canvas.drawArc(ringRect, rotationValue, 90f, false, paint)
        canvas.drawArc(ringRect, rotationValue + 180f, 90f, false, paint)

        paint.style = Paint.Style.FILL
        val coreGradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.parseColor("#FDFCFA"), Color.parseColor("#F5F2EC")),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = coreGradient
        canvas.drawCircle(centerX, centerY, baseRadius * 0.8f, paint)

        val pulseRadius = baseRadius * 0.4f * (0.8f + 0.4f * pulseValue)
        paint.shader = null
        paint.color = adjustAlpha(currentColor, 0.6f)
        canvas.drawCircle(centerX, centerY, pulseRadius, paint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
