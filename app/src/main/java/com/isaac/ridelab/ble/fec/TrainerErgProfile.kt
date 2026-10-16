package com.isaac.ridelab.ble.fec

/** Operating limits, independent of either protocol's wire format. */
data class TrainerErgProfile(val minErgWatts: Int, val maxErgWatts: Int, val incrementWatts: Int = 1) {
    init {
        require(minErgWatts >= 0)
        require(maxErgWatts <= 32767 && minErgWatts <= maxErgWatts)
        require(incrementWatts > 0)
    }
    fun clamp(watts: Int) = watts.coerceIn(minErgWatts, maxErgWatts)
    fun accepts(watts: Int) = watts in minErgWatts..maxErgWatts && (watts - minErgWatts) % incrementWatts == 0
    /** Only for displayed pending preferences, never for dispatching a requested command. */
    fun pending(watts: Int): Int = minErgWatts + (clamp(watts) - minErgWatts) / incrementWatts * incrementWatts
}

val ThinkRiderX2MaxProfile = TrainerErgProfile(minErgWatts = 50, maxErgWatts = 2000)
