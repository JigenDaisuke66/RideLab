package com.isaac.ridelab.ui.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayTrendTest {
    @Test fun historyIsBoundedAndMissingMeasurementsRemainMissing() {
        val data = OverlayTrend()
        repeat(1000) { data.add(it * 1000L, if (it == 999) null else 0, null) }
        assertEquals(120, data.points.size)
        assertNull(data.points.last().power)
        assertEquals(0, data.points.first().power)
        assertTrue(data.points.all { it.heartRate == null })
        data.add(999_500, 200, 100)
        assertEquals(120, data.points.size)
        assertNull(data.points.last().power)
    }
}
