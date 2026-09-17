package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile

internal fun parseErgTarget(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it in ThinkRiderX2MaxProfile.minErgWatts..ThinkRiderX2MaxProfile.maxErgWatts }
internal fun restoredErgTarget(saved: Int?, lastSent: Int?): Int = (saved ?: lastSent ?: 100)
    .coerceIn(ThinkRiderX2MaxProfile.minErgWatts, ThinkRiderX2MaxProfile.maxErgWatts)
