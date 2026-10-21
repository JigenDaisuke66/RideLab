package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.LiveRideData

/** Same renderer for real telemetry and the explicitly labelled settings example. */
class CapsuleView(context: Context) : View(context) {
    var readout = OverlayReadout(LiveRideData(), 0, false)
        set(value) { field = value; invalidate() }
    var samples: List<OverlayTrend.Point> = emptyList()
        set(value) { field = value; invalidate() }
    var collapsed = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val yellow = Color.rgb(255, 214, 0)
    private val red = Color.rgb(255, 100, 113)
    private val blue = Color.rgb(41, 182, 246)
    private val muted = Color.rgb(151, 162, 179)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val designWidth = if (collapsed) 260f else 480f
        canvas.save()
        canvas.scale(width / designWidth, height / 92f)
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(22, 24, 29)
        canvas.drawRoundRect(1f, 1f, designWidth - 1, 91f, 24f, 24f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x30FFFFFF
        canvas.drawRoundRect(1f, 1f, designWidth - 1, 91f, 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        val seconds = readout.elapsedSeconds
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, maxWidth: Float = 200f) {
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = size; paint.color = color
            if (paint.measureText(value) > maxWidth) paint.textSize *= maxWidth / paint.measureText(value)
            canvas.drawText(value, x, y, paint)
        }
        paint.color = if (readout.paused) yellow else red
        canvas.drawCircle(19f, 30f, 3f, paint)
        text(context.getString(R.string.ride_elapsed, seconds / 3600, seconds / 60 % 60, seconds % 60),
            28f, 36f, 15f, maxWidth = 83f)
        text(context.getString(if (readout.paused) R.string.ride_paused else R.string.ride_active),
            18f, 65f, 11f, if (readout.paused) yellow else Color.rgb(105, 240, 174), 94f)
        paint.color = 0x28FFFFFF; paint.strokeWidth = 1f
        canvas.drawLine(122f, 20f, 122f, 73f, paint)
        val noData = context.getString(R.string.history_no_data)
        text(context.getString(R.string.ui_power), 139f, 26f, 10f, yellow, 88f)
        text(readout.data.trainerPowerWatts?.let { context.getString(R.string.power_value, it) } ?: noData,
            137f, 57f, 28f, maxWidth = if (collapsed) 75f else 100f)
        if (!collapsed) {
            text(context.getString(R.string.capsule_recent), 139f, 76f, 9f, muted, 96f)
            val recent = samples.takeLast(15)
            val high = recent.mapNotNull { it.power }.maxOrNull()?.coerceAtLeast(1) ?: 1
            recent.forEachIndexed { i, point ->
                point.power?.let { v ->
                    val h = v.coerceAtLeast(0) / high.toFloat() * 44
                    paint.color = yellow
                    canvas.drawRoundRect(242f + i * 4.5f, 69 - h, 245f + i * 4.5f,
                        70f, 1.5f, 1.5f, paint)
                }
            }
            paint.color = 0x28FFFFFF
            canvas.drawLine(316f, 20f, 316f, 73f, paint)
            text(context.getString(R.string.ui_heart_rate), 330f, 25f, 9f, red, 70f)
            text(readout.data.heartRateBpm?.let { context.getString(R.string.hr_value, it) } ?: noData,
                330f, 43f, 17f, maxWidth = 78f)
            text(context.getString(R.string.cadence), 330f, 62f, 9f, blue, 70f)
            text(readout.data.cadenceRpm?.let { context.getString(R.string.cadence_value, it) } ?: noData,
                330f, 80f, 17f, maxWidth = 78f)
            fun line(top: Float, color: Int, value: (OverlayTrend.Point) -> Double?) {
                val values = recent.mapNotNull(value)
                val min = values.minOrNull() ?: return
                val range = ((values.maxOrNull() ?: min) - min).coerceAtLeast(1.0)
                var previous: OverlayTrend.Point? = null
                recent.forEachIndexed { i, p ->
                    val v = value(p)
                    fun y(n: Double) = top + 16 - ((n - min) / range * 16).toFloat()
                    paint.color = color; paint.strokeWidth = 1.8f
                    if (v != null) {
                        canvas.drawCircle(400f + i * 2, y(v), 1f, paint)
                        previous?.let { old -> if (p.time - old.time <= 3000)
                            value(old)?.let { canvas.drawLine(398f + i * 2, y(it), 400f + i * 2, y(v), paint) } }
                    }
                    previous = if (v == null) null else p
                }
            }
            line(27f, red) { it.heartRate?.toDouble() }
            line(64f, blue) { it.cadence }
        }
        canvas.restore()
    }
}
