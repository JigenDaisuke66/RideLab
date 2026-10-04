package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.*
import android.widget.*
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.OverlayStyle
import com.isaac.ridelab.data.repository.AppearanceSettings
import com.isaac.ridelab.locale.AppLocale
import com.isaac.ridelab.ui.theme.RideColors
import com.isaac.ridelab.ui.theme.accentColor
import com.isaac.ridelab.ui.theme.panelColor
import androidx.compose.ui.graphics.toArgb

/** Read-only window; no BLE ownership or trainer-control actions. Main thread only. */
class RideOverlayWindow(private val service: Context, private val close: () -> Unit) {
    private val windowContext = service.createWindowContext(
        service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private var context = AppLocale.overlayContext(windowContext, service)
    private val preferences = service.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
    private val appearanceSettings = AppearanceSettings(service)
    private var appearance = appearanceSettings.read()
    private val accent get() = appearance.effectiveOverlayPalette.accentColor.toArgb()
    private val panelColor get() = appearance.effectiveOverlayPalette.panelColor.toArgb()
    private val preferredWidth get() = if (appearance.overlayStyle == OverlayStyle.STRIP) 360 else 300
    private var transparency = preferences.getInt("transparency", 6).coerceIn(0, 80)
    private var fontPercent = preferences.getInt("font_percent", 100).coerceIn(80, 140)
    private val scaledTexts = mutableListOf<Pair<TextView, Float>>()
    private val manager = windowContext.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var power: TextView? = null
    private var hr: TextView? = null
    private var cadence: TextView? = null
    private var summary: TextView? = null
    private var collapsed = false
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = preferences.getInt("position_x", dp(16)); y = preferences.getInt("position_y", dp(120))
    }

    fun show(data: LiveRideData): Boolean {
        val selectedAppearance = appearanceSettings.read()
        if (selectedAppearance != appearance) {
            hide()
            appearance = selectedAppearance
        }
        val localized = AppLocale.overlayContext(windowContext, service)
        if (localized.resources.configuration.locales != context.resources.configuration.locales) {
            hide()
            context = localized
        }
        if (root == null) attach()
        val noData = context.getString(R.string.history_no_data)
        power?.text = data.trainerPowerWatts?.let { context.getString(R.string.power_value, it) } ?: noData
        hr?.text = data.heartRateBpm?.let { context.getString(R.string.hr_value, it) } ?: noData
        cadence?.text = data.cadenceRpm?.let { context.getString(R.string.cadence_value, it) } ?: noData
        summary?.text = context.getString(R.string.ui_overlay_compact, power?.text, hr?.text, cadence?.text)
        position(false)
        return root != null
    }
    private fun label(text: String, size: Float, accent: Boolean = false) = TextView(context).apply {
        this.text = text
        scaleText(this, size)
        setTextColor(if (accent) this@RideOverlayWindow.accent else RideColors.text.toArgb())
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun scaleText(view: TextView, baseSize: Float) {
        scaledTexts.add(view to baseSize)
        view.textSize = baseSize * fontPercent / 100f
    }
    private fun attach() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(4),dp(8),dp(12))
            elevation = dp(2).toFloat()
            // Fade the entire panel, including its text and controls.
            alpha = (100 - transparency) / 100f
            background = GradientDrawable().apply {
                if (appearance.overlayStyle == OverlayStyle.DASHBOARD) {
                    orientation = GradientDrawable.Orientation.TL_BR
                    colors = intArrayOf(panelColor, RideColors.background.toArgb())
                } else setColor(panelColor)
                cornerRadius = dp(if (appearance.overlayStyle == OverlayStyle.STRIP) 16 else 20).toFloat()
                setStroke(dp(1), accent)
            }
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = label(context.getString(R.string.overlay_title), 13f, true).apply {
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            contentDescription = context.getString(R.string.overlay_drag_hint)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(Button(context).apply {
            text = context.getString(R.string.overlay_close)
            scaleText(this, 12f)
            setTextColor(RideColors.secondaryText.toArgb()); background = null
            minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(56), dp(48)))
        panel.addView(header)
        summary = label(context.getString(R.string.history_no_data), 13f).apply {
            typeface = Typeface.MONOSPACE
            visibility = if (collapsed) View.VISIBLE else View.GONE
        }
        panel.addView(summary)
        val metrics = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun metric(titleId: Int): TextView {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(3),dp(4),dp(3)) }
            row.addView(label(context.getString(titleId),12f).apply { setTextColor(RideColors.secondaryText.toArgb()) }, LinearLayout.LayoutParams(0, -2, 1f))
            val value = label(context.getString(R.string.history_no_data), if (titleId == R.string.ui_power) 34f else 20f, titleId == R.string.ui_power).apply { typeface = Typeface.MONOSPACE }
            row.addView(value); metrics.addView(row); return value
        }
        fun tile(parent: LinearLayout, titleId: Int, size: Float): TextView {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(10))
            }
            column.addView(label(context.getString(titleId), 11f).apply { setTextColor(RideColors.secondaryText.toArgb()) })
            val value = label(context.getString(R.string.history_no_data), size, titleId == R.string.ui_power).apply {
                typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
            }
            column.addView(value)
            parent.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            return value
        }
        when (appearance.overlayStyle) {
            OverlayStyle.PANEL -> {
                power = metric(R.string.ui_power); hr = metric(R.string.ui_heart_rate); cadence = metric(R.string.cadence)
            }
            OverlayStyle.STRIP -> {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                power = tile(row, R.string.ui_power, 18f)
                hr = tile(row, R.string.ui_heart_rate, 16f)
                cadence = tile(row, R.string.cadence, 16f)
                metrics.addView(row)
            }
            OverlayStyle.DASHBOARD -> {
                val hero = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                power = tile(hero, R.string.ui_power, 40f); metrics.addView(hero)
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                hr = tile(row, R.string.ui_heart_rate, 20f)
                cadence = tile(row, R.string.cadence, 20f); metrics.addView(row)
            }
        }
        val transparencyLabel = label(context.getString(R.string.overlay_transparency, transparency), 12f)
        metrics.addView(transparencyLabel)
        metrics.addView(SeekBar(context).apply {
            max = 80
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            progress = transparency
            contentDescription = context.getString(R.string.overlay_transparency, transparency)
            minimumHeight = dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    transparency = value.coerceIn(0, 80)
                    panel.alpha = (100 - transparency) / 100f
                    val text = context.getString(R.string.overlay_transparency, transparency)
                    transparencyLabel.text = text
                    bar.contentDescription = text
                    preferences.edit().putInt("transparency", transparency).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        val fontLabel = label(context.getString(R.string.overlay_font_size, fontPercent), 12f)
        metrics.addView(fontLabel)
        metrics.addView(SeekBar(context).apply {
            min = 80
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            max = 140
            progress = fontPercent
            contentDescription = context.getString(R.string.overlay_font_size, fontPercent)
            minimumHeight = dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    fontPercent = value.coerceIn(80, 140)
                    scaledTexts.forEach { (view, base) -> view.textSize = base * fontPercent / 100f }
                    val text = context.getString(R.string.overlay_font_size, fontPercent)
                    fontLabel.text = text
                    bar.contentDescription = text
                    preferences.edit().putInt("font_percent", fontPercent).apply()
                    panel.post { if (root === panel) position(false) }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        // Keep header/close reachable on landscape and large-font displays.
        val body = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val window = manager.currentWindowMetrics
                val bars = window.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val available = (window.bounds.height() - bars.top - bars.bottom - dp(80)).coerceAtLeast(dp(48))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(metrics); visibility = if (collapsed) View.GONE else View.VISIBLE }
        panel.addView(body)
        title.setOnClickListener {
            title.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            android.transition.TransitionManager.beginDelayedTransition(panel,
                android.transition.AutoTransition().setDuration(160))
            collapsed = !collapsed
            body.visibility = if(collapsed) View.GONE else View.VISIBLE
            summary?.visibility = if(collapsed) View.VISIBLE else View.GONE
            panel.postDelayed({ if (root === panel) position(false) }, 180)
        }
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var moved = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        title.setOnTouchListener { view, event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; initialX=params.x; initialY=params.y; moved=false }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-downX; val dy=event.rawY-downY
                    if (kotlin.math.abs(dx)>slop || kotlin.math.abs(dy)>slop) moved=true
                    if(moved) { params.x=initialX+dx.toInt(); params.y=initialY+dy.toInt(); position(false) }
                }
                MotionEvent.ACTION_UP -> if(moved) {
                    position(true)
                    preferences.edit().putInt("position_x", params.x).putInt("position_y", params.y).apply()
                } else view.performClick()
                MotionEvent.ACTION_CANCEL -> position(true)
            }
            true
        }
        val bounds = manager.currentWindowMetrics.bounds
        params.width = minOf(dp(preferredWidth), bounds.width())
        root = panel
        try { manager.addView(panel,params) } catch(e: RuntimeException) {
            root=null; scaledTexts.clear(); power=null; hr=null; cadence=null; summary=null
            throw e
        }
        panel.post { if(root === panel) position(false) }
    }
    private fun position(snap: Boolean) {
        val view=root ?: return
        val bounds=manager.currentWindowMetrics.bounds
        val insets = manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val usableWidth = (bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        val usableHeight = (bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        params.width = minOf(dp(preferredWidth), usableWidth)
        val fitted=fitOverlay(params.x-insets.left,params.y-insets.top,usableWidth,usableHeight,params.width,view.height,snap)
        params.x=fitted.x+insets.left; params.y=fitted.y+insets.top
        try { manager.updateViewLayout(view,params) }
        catch(e: RuntimeException) { android.util.Log.e("RideLabOverlay","Window update failed",e); close() }
    }
    fun hide() {
        val view=root ?: return
        root=null; power=null; hr=null; cadence=null; summary=null
        scaledTexts.clear()
        try { manager.removeViewImmediate(view) }
        catch(e: RuntimeException) { android.util.Log.e("RideLabOverlay","Window removal failed",e) }
    }
}
