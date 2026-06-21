package com.jarvisai.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ExecutionVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.jarvisai.app.R.color.jarvis_accent)
        alpha = 128
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.jarvisai.app.R.color.jarvis_accent)
        alpha = 64
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val taps = mutableListOf<TapEffect>()
    private val trails = mutableListOf<TrailPath>()

    data class TapEffect(val x: Float, val y: Float, var radius: Float, var alpha: Int)
    data class TrailPath(val path: Path, var alpha: Int)

    fun addTap(x: Float, y: Float) {
        taps.add(TapEffect(x, y, 10f, 255))
        invalidate()
    }

    fun addTrail(path: Path) {
        trails.add(TrailPath(path, 150))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Taps
        val tapIterator = taps.iterator()
        while (tapIterator.hasNext()) {
            val tap = tapIterator.next()
            tapPaint.alpha = tap.alpha
            canvas.drawCircle(tap.x, tap.y, tap.radius, tap.paint())
            
            tap.radius += 5f
            tap.alpha -= 10
            if (tap.alpha <= 0) tapIterator.remove()
        }

        // Draw Trails
        val trailIterator = trails.iterator()
        while (trailIterator.hasNext()) {
            val trail = trailIterator.next()
            trailPaint.alpha = trail.alpha
            canvas.drawPath(trail.path, trailPaint)
            
            trail.alpha -= 5
            if (trail.alpha <= 0) trailIterator.remove()
        }

        if (taps.isNotEmpty() || trails.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
    
    private fun TapEffect.paint(): Paint = tapPaint.apply { alpha = this@paint.alpha }
}
