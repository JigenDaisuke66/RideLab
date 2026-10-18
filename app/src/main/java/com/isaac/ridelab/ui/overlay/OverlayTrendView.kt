package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import com.isaac.ridelab.R

/** Two quiet sparklines with independent scales. Nulls/time gaps never join segments. */
class OverlayTrendView(context: Context, private val data: OverlayTrend, private val powerColor: Int,
    private val hrColor: Int, private val fontPercent: Int = 100) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density get() = resources.displayMetrics.density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val points = data.points.toList()
        val end = points.lastOrNull()?.time ?: return
        val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f * fontPercent / 100,
            resources.displayMetrics)
        val gap = 8 * density
        val footer = textSize + gap
        val rowHeight = (height - footer) / 2f
        fun label(text: String, x: Float, y: Float, color: Int) {
            paint.color = color; paint.textSize = textSize; paint.style = Paint.Style.FILL
            paint.setShadowLayer(2 * density, 0f, density, Color.BLACK)
            canvas.drawText(text, x, y, paint)
            paint.clearShadowLayer()
        }
        fun series(top: Float, color: Int, title: Int, unit: Int, value: (OverlayTrend.Point) -> Int?) {
            val values = points.mapNotNull(value)
            label(context.getString(title), 0f, top + textSize, color)
            val current = points.lastOrNull()?.let(value)
            val reading = current?.let { context.getString(unit, it) } ?: context.getString(R.string.history_no_data)
            label(reading, width - paint.measureText(reading), top + textSize, color)
            val low = minOf(0, values.minOrNull() ?: 0)
            val high = maxOf(1, values.maxOrNull() ?: 1)
            val plotTop = top + textSize + gap
            val plotBottom = top + rowHeight - gap
            fun x(p: OverlayTrend.Point) = density + (width - 2 * density) * (p.time - end + 120_000) / 120_000f
            fun y(v: Int) = plotTop + (high - v).toFloat() / (high - low) * (plotBottom - plotTop)
            paint.strokeWidth = density
            paint.color = Color.argb(80, 211, 221, 232)
            canvas.drawLine(0f, plotBottom, width.toFloat(), plotBottom, paint)
            var previous: OverlayTrend.Point? = null
            points.forEach { point ->
                val v = value(point)
                val old = previous
                if (v != null) {
                    // A thin dark keyline keeps curves readable on both bright and dark apps.
                    for ((stroke, ink) in listOf(4f to Color.argb(180, 13, 19, 27), 2f to color)) {
                        paint.strokeWidth = stroke * density; paint.color = ink
                        paint.strokeCap = Paint.Cap.ROUND
                        canvas.drawPoint(x(point), y(v), paint)
                        if (old != null && point.time - old.time <= 3000)
                            value(old)?.let { canvas.drawLine(x(old), y(it), x(point), y(v), paint) }
                    }
                }
                previous = if (v == null) null else point
            }
        }
        series(0f, powerColor, R.string.ui_power, R.string.power_value) { it.power }
        series(rowHeight, hrColor, R.string.ui_heart_rate, R.string.hr_value) { it.heartRate }
        label(context.getString(R.string.overlay_chart_window), 0f, height - 2 * density,
            Color.rgb(211, 221, 232))
    }
}
