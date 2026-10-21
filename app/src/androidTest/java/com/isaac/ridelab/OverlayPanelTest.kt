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
