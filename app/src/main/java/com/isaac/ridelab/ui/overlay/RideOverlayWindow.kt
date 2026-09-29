package com.isaac.ridelab.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.*
import android.widget.*
import com.isaac.ridelab.R
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.locale.AppLocale

/** Read-only window; no BLE ownership or trainer-control actions. Main thread only. */
class RideOverlayWindow(private val service: Context, private val close: () -> Unit) {
    private val windowContext = service.createWindowContext(
        service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private var context = AppLocale.overlayContext(windowContext, service)
    private val preferences = service.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
    private var transparency = preferences.getInt("transparency", 6).coerceIn(0, 80)
    private val manager = windowContext.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var power: TextView? = null
    private var hr: TextView? = null
    private var cadence: TextView? = null
    private var collapsed = false
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = dp(16); y = dp(120) }

    fun show(data: LiveRideData): Boolean {
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
        position(false)
        return root != null
    }
    private fun label(text: String, size: Float, accent: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size
        setTextColor(if (accent) Color.rgb(118,214,195) else Color.rgb(225,237,233))
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun attach() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(4),dp(8),dp(12))
            elevation = dp(8).toFloat()
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(22,43,38), Color.rgb(13,23,21))).apply {
                cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(71,118,105))
                alpha = (255 * (100 - transparency) / 100)
            }
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = label(context.getString(R.string.overlay_title), 13f, true).apply {
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            contentDescription = context.getString(R.string.overlay_drag_hint)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(Button(context).apply {
            text = context.getString(R.string.overlay_close); textSize = 12f
            setTextColor(Color.rgb(225,237,233)); background = null
            minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(56), dp(48)))
        panel.addView(header)
        val metrics = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun metric(titleId: Int): TextView {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(3),dp(4),dp(3)) }
            row.addView(label(context.getString(titleId),12f), LinearLayout.LayoutParams(0, -2, 1f))
            val value = label(context.getString(R.string.history_no_data),22f,true)
            row.addView(value); metrics.addView(row); return value
        }
        power = metric(R.string.ui_power); hr = metric(R.string.ui_heart_rate); cadence = metric(R.string.cadence)
        val transparencyLabel = label(context.getString(R.string.overlay_transparency, transparency), 12f)
        metrics.addView(transparencyLabel)
        metrics.addView(SeekBar(context).apply {
            max = 80
            progress = transparency
            contentDescription = context.getString(R.string.overlay_transparency, transparency)
            minimumHeight = dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    transparency = value.coerceIn(0, 80)
                    panel.background.alpha = 255 * (100 - transparency) / 100
                    val text = context.getString(R.string.overlay_transparency, transparency)
                    transparencyLabel.text = text
                    bar.contentDescription = text
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) {
                    preferences.edit().putInt("transparency", transparency).apply()
                }
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        metrics.visibility = if (collapsed) View.GONE else View.VISIBLE
        panel.addView(metrics)
        title.setOnClickListener { collapsed = !collapsed; metrics.visibility = if(collapsed) View.GONE else View.VISIBLE; panel.post { position(true) } }
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
                MotionEvent.ACTION_UP -> if(moved) position(true) else view.performClick()
                MotionEvent.ACTION_CANCEL -> position(true)
            }
            true
        }
        val bounds = manager.currentWindowMetrics.bounds
        params.width = minOf(dp(250), bounds.width())
        root = panel
        try { manager.addView(panel,params) } catch(e: RuntimeException) { root=null; throw e }
        panel.post { if(root === panel) position(false) }
    }
    private fun position(snap: Boolean) {
        val view=root ?: return
        val bounds=manager.currentWindowMetrics.bounds
        val fitted=fitOverlay(params.x,params.y,bounds.width(),bounds.height(),params.width,view.height,snap)
        params.x=fitted.x; params.y=fitted.y
        try { manager.updateViewLayout(view,params) }
        catch(e: RuntimeException) { android.util.Log.e("RideLabOverlay","Window update failed",e); close() }
    }
    fun hide() {
        val view=root ?: return
        root=null; power=null; hr=null; cadence=null
        try { manager.removeViewImmediate(view) }
        catch(e: RuntimeException) { android.util.Log.e("RideLabOverlay","Window removal failed",e) }
    }
}
