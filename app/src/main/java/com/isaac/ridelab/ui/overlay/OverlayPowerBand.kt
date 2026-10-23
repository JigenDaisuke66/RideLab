package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.analytics.PowerAnalysisCalculator

enum class OverlayPowerBand { RECOVERY, AEROBIC, THRESHOLD, ANAEROBIC, UNKNOWN }

/** Display-only grouping of the existing seven zones, not a new analytics model.
 * Z1 blue, Z2/Z3 green, Z4/Z5 yellow, Z6/Z7 red. No FTP means no inferred zone.
 */
fun overlayPowerBand(watts: Int?, ftp: Int?): OverlayPowerBand {
    if (watts == null || watts < 0 || ftp == null || ftp <= 0) return OverlayPowerBand.UNKNOWN
    return when (PowerAnalysisCalculator.zone(watts.toDouble(), ftp)) {
        0 -> OverlayPowerBand.RECOVERY
        1, 2 -> OverlayPowerBand.AEROBIC
        3, 4 -> OverlayPowerBand.THRESHOLD
        else -> OverlayPowerBand.ANAEROBIC
    }
}

/** Fifteen one-second slots, oldest to newest; elapsed gaps stay empty. */
fun recentOverlayPower(samples: List<OverlayTrend.Point>): List<Int?> {
    val end = samples.lastOrNull()?.time ?: return List(15) { null }
    val slots = MutableList<Int?>(15) { null }
    samples.forEach {
        val age = end - it.time
        if (age in 0..14_999) slots[14 - (age / 1000).toInt()] = it.power?.takeIf { watts -> watts >= 0 }
    }
    return slots
}
