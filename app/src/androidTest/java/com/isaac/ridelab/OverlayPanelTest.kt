package com.isaac.ridelab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.ui.overlay.*
import org.junit.Assert.*
import org.junit.Test

/** Compile-only here: native rendering still requires physical visual acceptance. */
class OverlayPanelTest {
    @Test fun powerBarsRenderFourColorsWithFtp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = CapsuleView(instrumentation.targetContext).apply {
                ftpWatts = 200
                samples = listOf(40, 160, 200, 280).mapIndexed { index, watts ->
                    OverlayTrend.Point(index * 1000L, watts, null)
                }
            }
            view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(92, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 480, 92)
            val bitmap = Bitmap.createBitmap(480, 92, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val expected = listOf(android.graphics.Color.rgb(41, 182, 246),
                android.graphics.Color.rgb(102, 187, 106), android.graphics.Color.rgb(255, 214, 0),
                android.graphics.Color.rgb(255, 100, 113))
            expected.forEachIndexed { i, color ->
                val x = (243.5f + (11 + i) * 4.5f).toInt()
                assertEquals(color, bitmap.getPixel(x, 67))
            }
            bitmap.recycle()
        }
    }
    @Test fun capsuleRendersNarrowAndCollapsedWithoutDeviceOrService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (collapsed in listOf(false, true)) {
                val view = CapsuleView(instrumentation.targetContext)
                view.collapsed = collapsed
                view.readout = OverlayReadout(LiveRideData(0, null, null), 3723, true)
                val size = CapsuleGeometry.fit(100, 300, 150, 1f, collapsed)
                view.measure(View.MeasureSpec.makeMeasureSpec(size.first, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(size.second, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, size.first, size.second)
                val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                assertEquals(0, view.readout.data.trainerPowerWatts)
                assertTrue(bitmap.getPixel(size.first / 2, size.second / 2) != 0)
                bitmap.recycle()
            }
        }
    }
}
