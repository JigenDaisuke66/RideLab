package com.isaac.ridelab.ui.ride

import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.service.*
import com.isaac.ridelab.ui.chart.*

data class LiveTrendState(val charts: RideCharts = buildRideCharts(emptyList()), val startMillis: Long = 0)

/** Bounded UI-only history. Never writes samples or owns ride timing. */
class LiveRideTrend {
    private val samples = ArrayDeque<RideSample>()
    private var start: Long? = null
    private var lastWallTime: Long? = null
    private var pausedDuration = 0L
    fun update(ride: RideState, now: Long, live: LiveRideData): LiveTrendState {
        if (start != ride.startedAtMs) {
            samples.clear(); lastWallTime = null; start = ride.startedAtMs; pausedDuration = 0
        }
        if (!ride.ongoing) return state()
        val elapsed = ride.elapsedMs(now)
        // A missed UI update (background) or pause must never join two live segments.
        if ((lastWallTime?.let { now - it > 3000 } == true || pausedDuration != ride.pausedDurationMs) && samples.isNotEmpty() &&
            elapsed > samples.last().elapsedMillis + 1) {
            samples.addLast(LiveRideData().sample(0, 0, elapsed - 1))
        }
        val data = if (ride.phase == RidePhase.ACTIVE) live else LiveRideData()
        if (samples.lastOrNull()?.elapsedMillis == elapsed) samples.removeLast()
        samples.addLast(data.sample(0, 0, elapsed))
        lastWallTime = now
        pausedDuration = ride.pausedDurationMs
        while (samples.size > 302 || samples.first().elapsedMillis < elapsed - 300_000) samples.removeFirst()
        return state()
    }
    private fun state() = LiveTrendState(buildRideCharts(samples.toList()),
        samples.lastOrNull()?.elapsedMillis?.minus(300_000)?.coerceAtLeast(0) ?: 0)
}
