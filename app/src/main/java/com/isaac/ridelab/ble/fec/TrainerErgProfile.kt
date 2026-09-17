package com.isaac.ridelab.ble.fec

/** Device capability, independent of the FE-C wire format. Only this trainer is supported. */
data class TrainerErgProfile(val minErgWatts: Int, val maxErgWatts: Int) {
    init {
        require(minErgWatts >= FecProtocol.MIN_ENCODABLE_WATTS)
        require(maxErgWatts <= FecProtocol.MAX_ENCODABLE_WATTS && minErgWatts <= maxErgWatts)
    }
    fun clamp(watts: Int) = watts.coerceIn(minErgWatts, maxErgWatts)
}

val ThinkRiderX2MaxProfile = TrainerErgProfile(minErgWatts = 50, maxErgWatts = 2000)
