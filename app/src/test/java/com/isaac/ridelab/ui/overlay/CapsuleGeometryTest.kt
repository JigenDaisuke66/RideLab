package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.data.repository.OverlayPreferences
import org.junit.Assert.*
import org.junit.Test

class CapsuleGeometryTest {
    @Test fun scalesWidthAndHeightTogether() {
        val small = CapsuleGeometry.fit(60, 2000, 1000, 1f)
        val large = CapsuleGeometry.fit(120, 2000, 1000, 1f)
        assertEquals(small.first * 2, large.first)
        assertTrue(kotlin.math.abs(small.second * 2 - large.second) <= 1)
    }
    @Test fun fitsLandscapeAndNarrowScreensWithoutDistorting() {
        for ((w, h) in listOf(240 to 900, 900 to 60)) {
            val fit = CapsuleGeometry.fit(140, w, h, 3f)
            assertTrue(fit.first <= w && fit.second <= h)
            assertEquals(480.0 / 92, fit.first.toDouble() / fit.second, .15)
        }
    }
    @Test fun validatesPersistedSettingsAndIgnoresInvalidRanges() {
        assertEquals(OverlayPreferences(80, 60), OverlayPreferences.restore(999, -20))
        assertEquals(OverlayPreferences(0, 140), OverlayPreferences.restore(-1, 999))
    }
}
