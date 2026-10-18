package com.isaac.ridelab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.OverlayStyle
import com.isaac.ridelab.ui.overlay.*
import org.junit.Assert.*
import org.junit.Test

/** Native overlay view checks. Compile here; execute on a device separately. */
class OverlayPanelTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun allLayoutsDrawAtNarrowWidthAndLargeFontWithLocalizedTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            for (style in OverlayStyle.entries) {
                val panel = OverlayPanel(context, style, 140, 0xFF35D0BA.toInt(), OverlayTrend(),
                    false, false, {}, {}, {})
                panel.bind(OverlayReadout(LiveRideData(0, 81.0, 134), 3723, paused = true))
                val width = (240 * density).toInt()
                val height = (OverlaySize.restore().windowHeightDp(style, false, false, 140) * density).toInt()
                panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                panel.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                panel.draw(Canvas(bitmap))
                assertNull(panel.background)
                val texts = descendants(panel).filterIsInstance<TextView>()
                val timer = context.getString(R.string.ride_elapsed, 1L, 2L, 3L)
                assertTrue(texts.any { it.text.toString() == timer && it.width > 0 })
                assertTrue(texts.any { it.text.toString() == context.getString(R.string.ride_paused) })
                bitmap.recycle()
            }
        }
    }

    @Test fun buttonsDispatchOnceAndCollapsedPanelKeepsTimerAndPower() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            var closed = 0; var folded = 0; var charts = 0
            val panel = OverlayPanel(context, OverlayStyle.PANEL, 100, 0xFF35D0BA.toInt(), OverlayTrend(),
                false, false, { closed++ }, { folded++ }, { charts++ })
            panel.bind(OverlayReadout(LiveRideData(123, 80.0, null), 60, false))
            descendants(panel).single { it.contentDescription == context.getString(R.string.overlay_close) }.performClick()
            descendants(panel).single { it.contentDescription == context.getString(R.string.overlay_fold) }.performClick()
            descendants(panel).single { it.contentDescription == context.getString(R.string.overlay_chart_show) }.performClick()
            assertEquals(1, closed); assertEquals(1, folded); assertEquals(1, charts)
            panel.setCollapsed(true)
            assertEquals(View.GONE, panel.findViewWithTag<View>("overlay-body").visibility)
            assertTrue(descendants(panel).filterIsInstance<TextView>().any {
                it.text.toString() == context.getString(R.string.power_value, 123) && it.visibility == View.VISIBLE })
            panel.setCollapsed(false)
            assertEquals(View.VISIBLE, panel.findViewWithTag<View>("overlay-body").visibility)
        }
    }
}
