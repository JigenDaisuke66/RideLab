package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.isaac.ridelab.R

/** Separate scales for power and heart rate; nulls and gaps over 3 seconds break lines. */
class OverlayTrendView(context: Context, private val data: OverlayTrend, private val powerColor: Int,
    private val hrColor: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val points = data.points.toList()
        val end = points.lastOrNull()?.time ?: return
        fun series(top: Float, color: Int, title: Int, value: (OverlayTrend.Point) -> Int?) {
            paint.color = color; paint.strokeWidth = 2 * resources.displayMetrics.density
            paint.textSize = 11 * resources.displayMetrics.scaledDensity
            canvas.drawText(context.getString(title), 0f, top + paint.textSize, paint)
            val max = points.mapNotNull(value).maxOrNull()?.coerceAtLeast(1) ?: 1
            var previous: OverlayTrend.Point? = null
            fun x(p: OverlayTrend.Point) = width * (p.time - end + 120_000) / 120_000f
            fun y(v: Int) = top + height / 2f - 4 - v.coerceAtLeast(0) / max.toFloat() * (height / 2f - paint.textSize - 8)
            points.forEach { p ->
                val v = value(p)
                val old = previous
                if (v != null) {
                    canvas.drawPoint(x(p), y(v), paint)
                    if (old != null && p.time - old.time <= 3000) value(old)?.let { canvas.drawLine(x(old), y(it), x(p), y(v), paint) }
                }
                previous = if (v == null) null else p
            }
        }
        series(0f, powerColor, R.string.ui_power) { it.power }
        series(height / 2f, hrColor, R.string.ui_heart_rate) { it.heartRate }
    }
}
