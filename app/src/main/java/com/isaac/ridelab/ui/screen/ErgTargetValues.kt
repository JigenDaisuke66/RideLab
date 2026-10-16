package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile
import com.isaac.ridelab.ble.fec.TrainerErgProfile

internal fun parseErgTarget(text: String, profile: TrainerErgProfile = ThinkRiderX2MaxProfile): Int? =
    text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()?.takeIf(profile::accepts)
internal fun restoredErgTarget(saved: Int?, lastSent: Int?, profile: TrainerErgProfile = ThinkRiderX2MaxProfile): Int =
    profile.pending(saved ?: lastSent ?: 100)

internal fun adjustedErgTarget(current: Int, step: Int, profile: TrainerErgProfile = ThinkRiderX2MaxProfile): Int =
    profile.clamp((current.toLong() + step).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
