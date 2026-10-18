package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.OverlayStyle
import com.isaac.ridelab.service.RidePhase
import com.isaac.ridelab.service.RideState
import org.junit.Assert.*
import org.junit.Test

class OverlayReadoutTest {
    @Test fun usesExistingRideClockEvenWhenOverlayOpensLate() {
        val ride = RideState(RidePhase.ACTIVE, startedAtMs = 1000)
        val reading = OverlayReadout.from(LiveRideData(100, 81.0, 134), ride, 3_724_000)
        assertEquals(3723L, reading.elapsedSeconds)
        assertFalse(reading.paused)
        assertEquals(100, reading.data.trainerPowerWatts)
    }
    @Test fun pausedClockStaysFrozenAndResumeExcludesPause() {
        val paused = RideState(RidePhase.PAUSED, startedAtMs = 1000, pausedAtMs = 61000)
        assertEquals(60L, OverlayReadout.from(LiveRideData(), paused, 121000).elapsedSeconds)
        assertTrue(OverlayReadout.from(LiveRideData(), paused, 181000).paused)
        val resumed = paused.copy(phase = RidePhase.ACTIVE, pausedAtMs = null, pausedDurationMs = 120000)
        assertEquals(61L, OverlayReadout.from(LiveRideData(), resumed, 182000).elapsedSeconds)
    }
    @Test fun preservesZeroAndAbsentMeasurements() {
        val readout = OverlayReadout.from(LiveRideData(trainerPowerWatts = 0), RideState(), 10000)
        assertEquals(0L, readout.elapsedSeconds)
        assertEquals(0, readout.data.trainerPowerWatts)
        assertNull(readout.data.heartRateBpm)
        assertNull(readout.data.cadenceRpm)
    }
    @Test fun compactSizingFollowsStyleAndRetainsReadableMinimum() {
        val normal = OverlaySize.restore()
        OverlayStyle.entries.forEach { style ->
            val expanded = normal.windowHeightDp(style, false, false, 100)
            assertTrue(normal.windowHeightDp(style, false, true, 100) > expanded)
            assertTrue(normal.windowHeightDp(style, true, false, 100) < expanded)
            assertTrue(normal.windowHeightDp(style, false, false, 140) > expanded)
            assertTrue(OverlaySize.restore(80, 40).windowHeightDp(style, false, false, 80) >= 121)
        }
    }
}
