package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.*
import kotlin.math.abs

object BodyWeight {
    fun valid(kg: Double?) = kg == null || kg.isFinite() && kg in 20.0..300.0
    fun parse(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { valid(it) }
    fun wattsPerKg(watts: Double?, kg: Double?): Double? = if (watts != null && kg != null && valid(kg)) watts / kg else null
}

data class BestEffortResult(val seconds: Int, val watts: Double?, val wattsPerKg: Double?)
enum class PrStatus { UNAVAILABLE, FIRST, NEW, MATCH, BELOW }
data class HistoricalPrResult(val previousWatts: Double?, val status: PrStatus)
data class PowerZoneResult(val index: Int, val millis: Long, val percent: Double)
data class PowerAnalysis(val efforts: List<BestEffortResult>, val zones: List<PowerZoneResult>?, val prs: List<HistoricalPrResult> = emptyList())

object PowerAnalysisCalculator {
    val durations = listOf(5, 15, 30, 60, 300, 1200, 3600)
    // Conservative jitter tolerance: an interval above 1.5s is uncovered, not filled.
    // A reading covers [timestamp, next timestamp); the final reading has no assumed tail.
    private data class Interval(val start: Long, val end: Long, val watts: Double)
    private fun intervals(samples: List<RideSample>, duration: Long): List<Interval> {
        val points = samples.filter { it.elapsedMillis in 0..duration }.groupBy { it.elapsedMillis }
            .toSortedMap().map { (time, rows) -> time to if (rows.any { it.trainerPowerWatts == null || it.trainerPowerWatts < 0 }) null
                else rows.map { it.trainerPowerWatts!!.toDouble() }.average() }
        return points.zipWithNext().mapNotNull { (a, b) ->
            val power = a.second
            if (power != null && b.first - a.first in 1..1500) Interval(a.first, b.first, power) else null
        }
    }
    fun calculate(samples: List<RideSample>, session: RideSession): PowerAnalysis {
        val intervals = intervals(samples, session.durationMillis)
        val segments = mutableListOf<MutableList<Interval>>()
        for (interval in intervals) {
            if (segments.lastOrNull()?.last()?.end != interval.start) segments.add(mutableListOf())
            segments.last().add(interval)
        }
        val best = arrayOfNulls<Double>(durations.size)
        for (segment in segments) {
            val times = LongArray(segment.size + 1) { if (it == segment.size) segment.last().end else segment[it].start }
            val areas = DoubleArray(times.size)
            for (i in segment.indices) areas[i + 1] = areas[i] + segment[i].watts * (times[i + 1] - times[i])
            fun integral(time: Long): Double {
                val index = times.binarySearch(time)
                if (index >= 0) return areas[index]
                val left = -index - 2
                return areas[left] + (time - times[left]) * segment[left].watts
            }
            for ((index, seconds) in durations.withIndex()) {
                val window = seconds * 1000L
                if (times.last() - times.first() < window) continue
                // A piecewise-constant signal's moving integral has extrema where either
                // endpoint meets a boundary. Evaluate both sets, including partial intervals.
                for (boundary in times) for (start in longArrayOf(boundary, boundary - window)) {
                    if (start < times.first() || start + window > times.last()) continue
                    val mean = (integral(start + window) - integral(start)) / window
                    if (best[index] == null || mean > best[index]!!) best[index] = mean
                }
            }
        }
        val ftp = session.analyticsFtpWatts?.takeIf { it > 0 }
        val zoneTime = LongArray(7)
        if (ftp != null) intervals.forEach { zoneTime[zone(it.watts, ftp)] += it.end - it.start }
        val total = zoneTime.sum()
        return PowerAnalysis(durations.mapIndexed { i, seconds -> BestEffortResult(seconds, best[i], BodyWeight.wattsPerKg(best[i], session.bodyWeightKg)) },
            if (ftp == null || total == 0L) null else zoneTime.mapIndexed { i, time -> PowerZoneResult(i, time, time * 100.0 / total) })
    }
    // Continuous fractional boundaries avoid unassigned ranges between rounded percentages.
    fun zone(watts: Double, ftp: Int): Int {
        val percent = watts * 100 / ftp
        return when { percent < 55 -> 0; percent <= 75 -> 1; percent <= 90 -> 2; percent <= 105 -> 3; percent <= 120 -> 4; percent <= 150 -> 5; else -> 6 }
    }
    fun eligiblePrevious(selected: RideSession, candidate: RideSession) = candidate.status == StoredRideStatus.COMPLETED &&
        (candidate.startTimeEpochMillis < selected.startTimeEpochMillis ||
            candidate.startTimeEpochMillis == selected.startTimeEpochMillis && candidate.id < selected.id)

    fun compare(current: Double?, previous: Double?, status: StoredRideStatus): HistoricalPrResult {
        val result = when {
            current == null || status != StoredRideStatus.COMPLETED -> PrStatus.UNAVAILABLE
            previous == null -> PrStatus.FIRST
            abs(current - previous) <= 0.000001 -> PrStatus.MATCH
            current > previous -> PrStatus.NEW
            else -> PrStatus.BELOW
        }
        return HistoricalPrResult(previous, result)
    }
}
