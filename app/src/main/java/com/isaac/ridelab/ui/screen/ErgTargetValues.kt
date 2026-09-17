package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.ble.fec.FecProtocol

internal fun parseErgTarget(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it in FecProtocol.MIN_WATTS..FecProtocol.MAX_WATTS }
internal fun restoredErgTarget(saved: Int?, lastSent: Int?): Int = (saved ?: lastSent ?: 100)
    .coerceIn(FecProtocol.MIN_WATTS, FecProtocol.MAX_WATTS)
