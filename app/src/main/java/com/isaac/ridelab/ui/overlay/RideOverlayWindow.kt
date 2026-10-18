package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.*
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
    private val size get() = OverlaySize.restore(config.width, config.height)
    private val manager = windowContext.getSystemService(WindowManager::class.java)
    private var root: OverlayPanel? = null
    private var collapsed = false
    private var chartsExpanded = false
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
        trend.add(now, data.trainerPowerWatts, data.heartRateBpm)
        if (root == null) attach()
        root?.bind(OverlayReadout.from(data, ride, now))
        position(false)
        return root != null
    }

    private fun attach() {
        val panel = OverlayPanel(context, appearance.overlayStyle, config.font,
            appearance.effectiveOverlayPalette.accentColor.toArgb(), trend, collapsed, chartsExpanded,
            close = close,
            toggleCollapsed = {
                collapsed = !collapsed
                root?.setCollapsed(collapsed)
                position(false)
            },
            toggleCharts = {
                chartsExpanded = !chartsExpanded
                root?.setChartsExpanded(chartsExpanded)
                position(false)
            }).apply { alpha = (100 - config.transparency) / 100f }
        installDrag(panel.dragHandle)
        updateSize()
        root = panel
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
        params.width = minOf(dp(size.widthDp(appearance.overlayStyle)),
            (window.bounds.width() - insets.left - insets.right).coerceAtLeast(1))
        params.height = minOf(dp(size.windowHeightDp(appearance.overlayStyle, collapsed, chartsExpanded, config.font)),
            (window.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1))
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
