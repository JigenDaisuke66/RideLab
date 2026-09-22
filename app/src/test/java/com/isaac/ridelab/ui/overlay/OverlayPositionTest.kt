package com.isaac.ridelab.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test fun clampsDraggingToScreen() {
        assertEquals(OverlayPosition(0, 600), fitOverlay(-20, 900, 400, 800, 250, 200, false))
    }
    @Test fun snapsToNearestEdge() {
        assertEquals(OverlayPosition(0, 80), fitOverlay(20, 80, 400, 800, 250, 200, true))
        assertEquals(OverlayPosition(150, 80), fitOverlay(100, 80, 400, 800, 250, 200, true))
    }
    @Test fun handlesWindowLargerThanScreen() {
        assertEquals(OverlayPosition(0, 0), fitOverlay(40, 40, 200, 100, 250, 200, true))
    }
    @Test fun rotationKeepsWindowReachable() {
        assertEquals(OverlayPosition(150, 200), fitOverlay(500, 700, 400, 400, 250, 200, false))
    }
}
