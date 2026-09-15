package com.isaac.ridelab.ble.power

import com.isaac.ridelab.data.model.TimedMetric

class CrankCadenceCalculator {
    private var previous: CrankRevolutionData? = null
    private var previousAtMs = 0L
    private var latest: TimedMetric<Double>? = null

    fun reset() { previous = null; latest = null }

    fun update(crank: CrankRevolutionData?, nowMs: Long): TimedMetric<Double>? {
        if (crank == null) { reset(); return null }
        val old = previous
        // Event time wraps every 64 s; after that gap its delta is ambiguous.
        if (old == null || nowMs - previousAtMs >= 64_000 || nowMs < previousAtMs) {
            previous = crank
            previousAtMs = nowMs
            latest = null
            return null
        }
        if (crank == old) return latest // Do not refresh the age of an unchanged reading.
        previous = crank
        previousAtMs = nowMs
        val turns = (crank.revolutions - old.revolutions) and 0xffff
        val ticks = (crank.lastEventTime1024 - old.lastEventTime1024) and 0xffff
        val rpm = if (ticks > 0) turns * 60.0 * 1024.0 / ticks else 0.0
        // Reject counter resets/invalid deltas rather than displaying an enormous cadence.
        latest = if (turns > 0 && rpm > 0 && rpm <= 300) TimedMetric(rpm, nowMs) else null
        return latest
    }
}
