package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.OverlayStyle
import com.isaac.ridelab.ui.theme.RideColors
import androidx.compose.ui.graphics.toArgb

/** Native, transparent HUD renderer. No settings writes, ride clock or device commands. */
internal class OverlayPanel(
    context: Context,
    style: OverlayStyle,
    private val font: Int,
    private val accent: Int,
    trend: OverlayTrend,
    collapsed: Boolean,
    chartsExpanded: Boolean,
    close: () -> Unit,
    private val toggleCollapsed: () -> Unit,
    private val toggleCharts: () -> Unit,
) : LinearLayout(context) {
    private val white = Color.rgb(245, 248, 252)
    private val muted = Color.rgb(211, 221, 232)
    private val heartColor = RideColors.heartRate.toArgb()
    private val cadenceColor = RideColors.cadence.toArgb()
    private val timer = text(22f, white, numeric = true)
    private val status = text(10f, muted)
    private val statusDot = View(context)
    private val collapseButton = iconButton(R.drawable.overlay_collapse, R.string.overlay_fold, toggleCollapsed)
    private val compactPower = text(22f, accent, numeric = true)
    private val power: TextView
    private val hr: TextView
    private val cadence: TextView
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val chartView = OverlayTrendView(context, trend, accent, heartColor, font)
    private val chartToggle = LinearLayout(context).apply { gravity = Gravity.CENTER; minimumHeight = dp(48) }
    private val chartToggleText = text(10f, muted)
    private val chartToggleIcon = icon(R.drawable.overlay_expand, muted, 16)
    private val compact = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(42) }
    val dragHandle: View

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(4), dp(10), dp(8))
        background = null
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56) }
        val clockGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = textHeight(56)
            contentDescription = context.getString(R.string.overlay_drag_hint)
            isClickable = true
            setOnClickListener { toggleCollapsed() }
        }
        val stateRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        stateRow.addView(statusDot, LayoutParams(dp(5), dp(5)).apply { marginEnd = dp(5) })
        stateRow.addView(status)
        clockGroup.addView(stateRow)
        clockGroup.addView(timer, LayoutParams(-1, textHeight(30)))
        header.addView(clockGroup, LayoutParams(0, -2, 1f))
        header.addView(collapseButton, LayoutParams(dp(48), dp(48)))
        header.addView(iconButton(R.drawable.overlay_close, R.string.overlay_close, close), LayoutParams(dp(48), dp(48)))
        addView(header)
        dragHandle = clockGroup

        // The collapsed HUD still exposes the ride timer and primary measurement.
        compact.addView(icon(R.drawable.overlay_power, accent, 16))
        compact.addView(text(10f, muted).apply { setText(R.string.ui_power) },
            LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        compact.addView(compactPower, LayoutParams(dp(125), -2))
        addView(compact)

        if (style == OverlayStyle.STRIP) {
            val strip = LinearLayout(context)
            power = tile(strip, R.string.ui_power, R.drawable.overlay_power, accent, 23f)
            hr = tile(strip, R.string.ui_heart_rate, R.drawable.overlay_heart, heartColor, 23f)
            cadence = tile(strip, R.string.cadence, R.drawable.overlay_cadence, cadenceColor, 23f)
            content.addView(strip)
        } else {
            val hero = LinearLayout(context).apply {
                orientation = if (style == OverlayStyle.DASHBOARD) VERTICAL else HORIZONTAL
                gravity = if (style == OverlayStyle.DASHBOARD) Gravity.CENTER else Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(8))
            }
            val title = metricTitle(R.string.ui_power, R.drawable.overlay_power, accent)
            if (style == OverlayStyle.DASHBOARD) hero.addView(title)
            else hero.addView(title, LayoutParams(0, -2, 1f))
            power = text(if (style == OverlayStyle.DASHBOARD) 46f else 38f, accent, numeric = true).apply {
                gravity = if (style == OverlayStyle.DASHBOARD) Gravity.CENTER else Gravity.END
            }
            hero.addView(power, LayoutParams(if (style == OverlayStyle.DASHBOARD) -1 else dp(144), textHeight(62)))
            content.addView(hero)
            content.addView(View(context).apply { setBackgroundColor(Color.argb(60, 211, 221, 232)) },
                LayoutParams(-1, dp(1)))
            val row = LinearLayout(context)
            hr = tile(row, R.string.ui_heart_rate, R.drawable.overlay_heart, heartColor, 26f)
            cadence = tile(row, R.string.cadence, R.drawable.overlay_cadence, cadenceColor, 26f)
            content.addView(row)
        }
        chartToggle.addView(icon(R.drawable.overlay_chart, accent, 14))
        chartToggle.addView(chartToggleText, LayoutParams(-2, -2).apply { marginStart = dp(6); marginEnd = dp(4) })
        chartToggle.addView(chartToggleIcon)
        chartToggle.background = ripple()
        chartToggle.isFocusable = true
        chartToggle.setOnClickListener { toggleCharts() }
        content.addView(chartToggle)
        content.addView(chartView, LayoutParams(-1, dp(140)))
        val body = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(content)
        }
        addView(body, LayoutParams(-1, 0, 1f))
        // Tag avoids exposing Android view hierarchy to the window owner.
        body.tag = "overlay-body"
        setCollapsed(collapsed)
        setChartsExpanded(chartsExpanded)
    }

    fun bind(readout: OverlayReadout) {
        val seconds = readout.elapsedSeconds
        timer.text = context.getString(R.string.ride_elapsed, seconds / 3600, seconds / 60 % 60, seconds % 60)
        timer.contentDescription = context.getString(R.string.history_field,
            context.getString(R.string.overlay_ride_time), timer.text)
        status.setText(if (readout.paused) R.string.ride_paused else R.string.ride_active)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (readout.paused) cadenceColor else accent)
        }
        val noData = context.getString(R.string.history_no_data)
        power.text = readout.data.trainerPowerWatts?.let { context.getString(R.string.power_value, it) } ?: noData
        compactPower.text = power.text
        hr.text = readout.data.heartRateBpm?.let { context.getString(R.string.hr_value, it) } ?: noData
        cadence.text = readout.data.cadenceRpm?.let { context.getString(R.string.cadence_value, it) } ?: noData
        chartView.invalidate()
    }

    fun setCollapsed(value: Boolean) {
        compact.visibility = if (value) VISIBLE else GONE
        findViewWithTag<View>("overlay-body").visibility = if (value) GONE else VISIBLE
        collapseButton.setImageResource(if (value) R.drawable.overlay_expand else R.drawable.overlay_collapse)
        collapseButton.contentDescription = context.getString(if (value) R.string.overlay_unfold else R.string.overlay_fold)
    }

    fun setChartsExpanded(value: Boolean) {
        chartView.visibility = if (value) VISIBLE else GONE
        chartToggleText.setText(if (value) R.string.overlay_chart_hide else R.string.overlay_chart_show)
        chartToggle.contentDescription = chartToggleText.text
        chartToggleIcon.setImageResource(if (value) R.drawable.overlay_collapse else R.drawable.overlay_expand)
    }

    private fun tile(parent: LinearLayout, title: Int, glyph: Int, color: Int, valueSize: Float): TextView {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(3), dp(10), dp(3), dp(2))
        }
        column.addView(metricTitle(title, glyph, color))
        val value = text(valueSize, white, numeric = true).apply { gravity = Gravity.CENTER }
        column.addView(value, LayoutParams(-1, textHeight(44)))
        parent.addView(column, LayoutParams(0, -2, 1f))
        return value
    }
    private fun metricTitle(title: Int, glyph: Int, color: Int) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(icon(glyph, color, 14))
        addView(text(10f, muted).apply { setText(title) }, LayoutParams(-2, -2).apply { marginStart = dp(5) })
    }
    private fun icon(resource: Int, color: Int, size: Int) = ImageView(context).apply {
        setImageResource(resource); imageTintList = ColorStateList.valueOf(color)
        layoutParams = LayoutParams(dp(size), dp(size))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun iconButton(resource: Int, description: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(resource); imageTintList = ColorStateList.valueOf(muted)
        contentDescription = context.getString(description)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = ripple()
        setOnClickListener { action() }
    }
    private fun ripple() = RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), null,
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
    private fun text(size: Float, color: Int, numeric: Boolean = false) = TextView(context).apply {
        setTextColor(color)
        typeface = if (numeric) Typeface.create("sans-serif-condensed", Typeface.BOLD)
            else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isSingleLine = true
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        setAutoSizeTextTypeUniformWithConfiguration(9, (size * font / 100).toInt().coerceAtLeast(10), 1,
            android.util.TypedValue.COMPLEX_UNIT_SP)
        textSize = size * font / 100
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.rgb(13, 19, 27))
        if (numeric) fontFeatureSettings = "tnum"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun textHeight(value: Int) = dp((value * font / 100f).toInt())
}
