package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession

data class SensorCoverage(val millis: Long, val durationMillis: Long) {
    val percent: Double? get() = if (durationMillis > 0) millis * 100.0 / durationMillis else null
}
data class CadenceBand(val index: Int, val millis: Long, val percent: Double)
data class RideInsights(
    val workKilojoules: Double?,
    val recordedDistanceKm: Double?,
    val averageWattsPerKg: Double?,
    val zeroPowerMillis: Long?,
    val pedalingMillis: Long?,
    val powerCoverage: SensorCoverage,
    val heartRateCoverage: SensorCoverage,
    val cadenceCoverage: SensorCoverage,
    val speedCoverage: SensorCoverage,
    val cadenceBands: List<CadenceBand>?,
) {
    val energyEstimate: CyclingEnergyEstimate? get() = CyclingEnergyCalculator.estimate(workKilojoules)
}

/** Read-only raw-sample analysis, separate from existing NP, efforts and zones.
 * Same conservative 1.5s jitter tolerance as best efforts: [sample, next sample).
 * No assumed tail, interpolation, null-to-zero conversion, or bridging recording gaps.
 * Work is mechanical energy, NOT metabolic kcal. Distance uses recorded speed only.
 */
object RideInsightsCalculator {
    fun cadenceBand(rpm: Double): Int = when {
        rpm == 0.0 -> 0
        rpm < 60 -> 1
        rpm < 80 -> 2
        rpm < 100 -> 3
        else -> 4
    }

    fun calculate(samples: List<RideSample>, session: RideSession): RideInsights {
        val duration = session.durationMillis.coerceAtLeast(0)
        // Duplicate timestamps do not create coverage or count energy twice.
        val points = samples.filter { it.elapsedMillis in 0..duration }
            .groupBy { it.elapsedMillis }.toSortedMap().values.map { it.last() }
        var powerTime = 0L; var hrTime = 0L; var cadenceTime = 0L; var speedTime = 0L
        var zeroTime = 0L; var pedalingTime = 0L
        var work = 0.0; var distance = 0.0
        val bands = LongArray(5)
        fun Double?.valid() = this != null && isFinite() && this >= 0.0
        for (i in 0 until points.lastIndex) {
            val sample = points[i]
            val dt = points[i + 1].elapsedMillis - sample.elapsedMillis
            if (dt !in 1..1500) continue
            sample.trainerPowerWatts?.takeIf { it >= 0 }?.let {
                powerTime += dt
                work += it.toDouble() * dt / 1_000_000.0
                if (it == 0) zeroTime += dt
            }
            if (sample.heartRateBpm?.let { it > 0 } == true) hrTime += dt
            val cadence = sample.cadenceRpm
            if (cadence.valid()) {
                cadenceTime += dt
                if (cadence!! > 0) pedalingTime += dt
                bands[cadenceBand(cadence)] += dt
            }
            val speed = sample.speedKph
            if (speed.valid()) {
                speedTime += dt
                distance += speed!! * dt / 3_600_000.0
            }
        }
        // Same null-aware sample mean used by the existing history AVG projection.
        val powerValues = points.mapNotNull { it.trainerPowerWatts?.takeIf { watts -> watts >= 0 } }
        val average = if (powerValues.isEmpty()) null else powerValues.average()
        return RideInsights(
            work.takeIf { powerTime > 0 }, distance.takeIf { speedTime > 0 },
            BodyWeight.wattsPerKg(average, session.bodyWeightKg),
            zeroTime.takeIf { powerTime > 0 }, pedalingTime.takeIf { cadenceTime > 0 },
            SensorCoverage(powerTime, duration), SensorCoverage(hrTime, duration),
            SensorCoverage(cadenceTime, duration), SensorCoverage(speedTime, duration),
            if (cadenceTime == 0L) null else bands.mapIndexed { index, millis ->
                CadenceBand(index, millis, millis * 100.0 / cadenceTime)
            },
        )
    }
}
