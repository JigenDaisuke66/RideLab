package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.*
import com.isaac.ridelab.R
import androidx.compose.ui.graphics.toArgb
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.repository.AppearanceSettings
import com.isaac.ridelab.data.repository.OverlayPreferencesStore
import com.isaac.ridelab.locale.AppLocale
import com.isaac.ridelab.service.RideState
import com.isaac.ridelab.ui.theme.accentColor

/** Read-only window host. Rendering lives in OverlayPanel; service owns the ride and clock. */
class RideOverlayWindow(private val service: Context, private val close: () -> Unit) {
    private val windowContext = service.createWindowContext(
        service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private var context = AppLocale.overlayContext(windowContext, service)
    private val preferences = service.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
    private val appearanceSettings = AppearanceSettings(service)
    private var appearance = appearanceSettings.read()
    private val preferenceStore = OverlayPreferencesStore(service)
    private var config = preferenceStore.read()
    private var capsule: CapsuleView? = null
    private var lastReadout = OverlayReadout(LiveRideData(), 0, false)
    private val manager = windowContext.getSystemService(WindowManager::class.java)
    private var root: android.widget.FrameLayout? = null
    private var collapsed = false
    private var sessionStart: Long? = null
    private var trend = OverlayTrend()
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = preferences.getInt("position_x", dp(16)); y = preferences.getInt("position_y", dp(120))
    }

    fun show(data: LiveRideData, ride: RideState, keepScreenOn: Boolean = false): Boolean {
        params.flags = if (keepScreenOn) params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            else params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        val nextConfig = preferenceStore.read()
        val nextAppearance = appearanceSettings.read()
        val localized = AppLocale.overlayContext(windowContext, service)
        if (config != nextConfig || appearance != nextAppearance ||
            localized.resources.configuration.locales != context.resources.configuration.locales) {
            hide()
            config = nextConfig; appearance = nextAppearance; context = localized
        }
        if (sessionStart != ride.startedAtMs) {
            hide(); sessionStart = ride.startedAtMs; trend = OverlayTrend()
        }
        val now = SystemClock.elapsedRealtime()
        trend.add(now, data.trainerPowerWatts, data.heartRateBpm, data.cadenceRpm)
        lastReadout = OverlayReadout.from(data, ride, now)
        if (root == null) attach()
        capsule?.apply {
            readout = lastReadout
            samples = trend.points.toList()
            contentDescription = context.getString(R.string.ui_overlay_compact,
                data.trainerPowerWatts?.let { context.getString(R.string.power_value, it) } ?: context.getString(R.string.history_no_data),
                data.heartRateBpm?.let { context.getString(R.string.hr_value, it) } ?: context.getString(R.string.history_no_data),
                data.cadenceRpm?.let { context.getString(R.string.cadence_value, it) } ?: context.getString(R.string.history_no_data))
        }
        position(false)
        return root != null
    }

    private fun attach() {
        updateSize()
        val panel = android.widget.FrameLayout(context).apply { alpha = (100 - config.transparency) / 100f }
        capsule = CapsuleView(context).apply {
            collapsed = this@RideOverlayWindow.collapsed; readout = lastReadout; samples = trend.points.toList()
        }
        panel.addView(capsule, android.widget.FrameLayout.LayoutParams(-1, -1))
        fun button(icon: Int, label: Int, gravity: Int, action: () -> Unit) {
            panel.addView(android.widget.ImageButton(context).apply {
                setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.LTGRAY)
                val pad = minOf(dp(8), params.height / 8)
                background = null; setPadding(pad, pad, pad, pad)
                contentDescription = context.getString(label); setOnClickListener { action() }
            }, android.widget.FrameLayout.LayoutParams(minOf(dp(32), params.height / 2), minOf(dp(32), params.height / 2), gravity))
        }
        button(R.drawable.overlay_close, R.string.overlay_close, Gravity.TOP or Gravity.RIGHT, close)
        button(if (collapsed) R.drawable.overlay_expand else R.drawable.overlay_collapse,
            if (collapsed) R.string.overlay_unfold else R.string.overlay_fold, Gravity.BOTTOM or Gravity.RIGHT) {
            collapsed = !collapsed; hide(); attach()
        }
        panel.contentDescription = context.getString(R.string.overlay_drag_hint)
        panel.setOnClickListener { collapsed = !collapsed; hide(); attach() }
        installDrag(panel)
        updateSize(); root = panel
        try { manager.addView(panel, params) }
        catch (error: RuntimeException) { root = null; throw error }
        panel.post { if (root === panel) position(false) }
    }

    private fun installDrag(handle: View) {
        var downX = 0f; var downY = 0f
        var initialX = 0; var initialY = 0
        var moved = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    initialX = params.x; initialY = params.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) { params.x = initialX + dx.toInt(); params.y = initialY + dy.toInt(); position(false) }
                }
                MotionEvent.ACTION_UP -> if (moved) {
                    position(true)
                    preferences.edit().putInt("position_x", params.x).putInt("position_y", params.y).apply()
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> position(true)
            }
            true
        }
    }

    private fun updateSize() {
        val window = manager.currentWindowMetrics
        val insets = window.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val fitted = CapsuleGeometry.fit(config.size,
            (window.bounds.width() - insets.left - insets.right).coerceAtLeast(1),
            (window.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1),
            context.resources.displayMetrics.density, collapsed)
        params.width = fitted.first; params.height = fitted.second
    }

    private fun position(snap: Boolean) {
        val view = root ?: return
        updateSize()
        val window = manager.currentWindowMetrics
        val insets = window.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val width = (window.bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        val height = (window.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        val fitted = fitOverlay(params.x - insets.left, params.y - insets.top, width, height,
            params.width, params.height, snap)
        params.x = fitted.x + insets.left; params.y = fitted.y + insets.top
        try { manager.updateViewLayout(view, params) }
        catch (error: RuntimeException) { android.util.Log.e("RideLabOverlay", "Window update failed", error); close() }
    }

    fun hide() {
        val view = root ?: return
        root = null
        try { manager.removeViewImmediate(view) }
        catch (error: RuntimeException) { android.util.Log.e("RideLabOverlay", "Window removal failed", error) }
    }
}
