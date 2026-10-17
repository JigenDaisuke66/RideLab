package com.isaac.ridelab.ui.ride

import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.service.*
import org.junit.Assert.*
import org.junit.Test

class LiveRideTrendTest {
    private val ride = RideState(RidePhase.ACTIVE, startedAtMs = 1000)
    @Test fun zeroAndMissingMetricsRemainDistinct() {
        val trend = LiveRideTrend()
        trend.update(ride, 1000, LiveRideData(0, 80.0, 120))
        trend.update(ride, 2000, LiveRideData())
        val data = trend.update(ride, 3000, LiveRideData(100, 85.0, 130)).charts
        assertEquals(2, data.power.segments.size)
        assertEquals(0.0, data.power.segments.first().first().value, 0.0)
        assertEquals(2, data.heartRate.segments.size)
        assertEquals(2, data.cadence.segments.size)
    }
    @Test fun longRideIsBoundedAndNewRideResets() {
        val trend = LiveRideTrend()
        var state = LiveTrendState()
        repeat(1000) { state = trend.update(ride, 1000L + it * 1000, LiveRideData(100)) }
        assertTrue(state.charts.power.segments.flatten().size <= 302)
        assertEquals(699000L, state.startMillis)
        val next = trend.update(ride.copy(startedAtMs = 2000000), 2000000, LiveRideData(200))
        assertEquals(1, next.charts.power.segments.flatten().size)
        assertEquals(0L, next.charts.endMillis)
    }
    @Test fun backgroundAndPauseBreakLinesWithoutFabricatedSamples() {
        val trend = LiveRideTrend()
        trend.update(ride, 1000, LiveRideData(100))
        val afterGap = trend.update(ride, 10000, LiveRideData(120))
        assertEquals(2, afterGap.charts.power.segments.size)
        val paused = ride.copy(phase = RidePhase.PAUSED, pausedAtMs = 11000)
        trend.update(paused, 11000, LiveRideData(150))
        trend.update(paused, 15000, LiveRideData(150))
        val resumed = trend.update(ride.copy(pausedDurationMs = 5000), 17000, LiveRideData(130))
        assertEquals(3, resumed.charts.power.segments.size)
        assertFalse(resumed.charts.power.segments.flatten().any { it.value == 150.0 })
    }
}
