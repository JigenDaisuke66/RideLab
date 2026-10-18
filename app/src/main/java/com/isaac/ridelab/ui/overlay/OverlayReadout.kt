package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.service.RidePhase
import com.isaac.ridelab.service.RideState

/** Presentation snapshot only; the service remains the sole ride clock owner. */
data class OverlayReadout(val data: LiveRideData, val elapsedSeconds: Long, val paused: Boolean) {
    companion object {
        fun from(data: LiveRideData, ride: RideState, nowMs: Long) =
            OverlayReadout(data, ride.elapsedMs(nowMs) / 1000, ride.phase == RidePhase.PAUSED)
    }
}
