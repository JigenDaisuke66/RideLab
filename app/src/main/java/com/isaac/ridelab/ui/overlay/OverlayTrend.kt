package com.isaac.ridelab.ui.overlay

/** Bounded presentation history; raw recording remains owned by the service. */
class OverlayTrend {
    data class Point(val time: Long, val power: Int?, val heartRate: Int?)
    val points = ArrayDeque<Point>()
    fun add(time: Long, power: Int?, heartRate: Int?) {
        if (points.lastOrNull()?.let { time - it.time < 900 } == true) return
        points.addLast(Point(time, power, heartRate))
        while (points.size > 120 || points.first().time < time - 120_000) points.removeFirst()
    }
}
