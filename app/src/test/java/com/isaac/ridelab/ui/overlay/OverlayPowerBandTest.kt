package com.isaac.ridelab.ui.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayPowerBandTest {
    @Test fun groupsExistingZonesIntoFourColorsAtExactBoundaries() {
        val ftp = 200
        mapOf(0 to OverlayPowerBand.RECOVERY, 109 to OverlayPowerBand.RECOVERY,
            110 to OverlayPowerBand.AEROBIC, 150 to OverlayPowerBand.AEROBIC,
            180 to OverlayPowerBand.AEROBIC, 181 to OverlayPowerBand.THRESHOLD,
            200 to OverlayPowerBand.THRESHOLD, 210 to OverlayPowerBand.THRESHOLD,
            240 to OverlayPowerBand.THRESHOLD, 241 to OverlayPowerBand.ANAEROBIC,
            300 to OverlayPowerBand.ANAEROBIC, 2000 to OverlayPowerBand.ANAEROBIC)
            .forEach { (watts, expected) -> assertEquals("watts=$watts", expected, overlayPowerBand(watts, ftp)) }
    }
    @Test fun sameWattsDependOnFtpNotAbsolutePower() {
        assertEquals(OverlayPowerBand.ANAEROBIC, overlayPowerBand(200, 100))
        assertEquals(OverlayPowerBand.THRESHOLD, overlayPowerBand(200, 200))
        assertEquals(OverlayPowerBand.AEROBIC, overlayPowerBand(200, 300))
    }
    @Test fun absentOrInvalidFtpHasNoInferredZone() {
        listOf(null, 0, -1).forEach {
            assertEquals(OverlayPowerBand.UNKNOWN, overlayPowerBand(100, it))
        }
        assertEquals(OverlayPowerBand.UNKNOWN, overlayPowerBand(null, 200))
        assertEquals(OverlayPowerBand.UNKNOWN, overlayPowerBand(-1, 200))
    }
    @Test fun historyKeepsFifteenSlotsZeroAndMissingGaps() {
        val points = listOf(OverlayTrend.Point(0, 2000, null),
            OverlayTrend.Point(16000, 0, null), OverlayTrend.Point(18000, null, null),
            OverlayTrend.Point(19000, -1, null), OverlayTrend.Point(20000, 220, null))
        val bars = recentOverlayPower(points)
        assertEquals(15, bars.size)
        assertEquals(0, bars[10])
        assertNull(bars[11]); assertNull(bars[12]); assertNull(bars[13])
        assertEquals(220, bars.last())
        assertFalse(bars.contains(2000))
        assertTrue(recentOverlayPower(emptyList()).all { it == null })
    }
}
