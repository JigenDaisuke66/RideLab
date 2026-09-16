package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.RideSample
import kotlin.math.pow

data class TrainingMetrics(val np: Double?, val ftp: Int?, val intensityFactor: Double?, val tss: Double?)

object TrainingAnalytics {
    fun validFtp(value: Int?) = value == null || value in 50..500

    // One value per elapsed second (mean if duplicate rows). Any null/negative reading
    // invalidates that second. Missing seconds reset the 30-second window: no interpolation.
    fun normalizedPower(samples: List<RideSample>, durationMillis: Long): Double? {
        if (durationMillis < 30_000) return null
        val seconds = samples.filter { it.elapsedMillis >= 0 && it.elapsedMillis < durationMillis }
            .groupBy { it.elapsedMillis / 1000 }.toSortedMap()
        val window = ArrayDeque<Double>()
        var sum = 0.0
        var fourthSum = 0.0
        var windows = 0
        var previous: Long? = null
        for ((second, rows) in seconds) {
            val valid = rows.all { it.trainerPowerWatts != null && it.trainerPowerWatts >= 0 }
            if (previous != null && second != previous + 1 || !valid) {
                window.clear(); sum = 0.0
            }
            previous = second
            if (!valid) continue
            val power = rows.map { it.trainerPowerWatts!!.toDouble() }.average()
            window.addLast(power); sum += power
            if (window.size > 30) sum -= window.removeFirst()
            if (window.size == 30) { fourthSum += (sum / 30).pow(4); windows++ }
        }
        return if (windows == 0) null else (fourthSum / windows).pow(0.25)
    }

    fun calculate(np: Double?, ftp: Int?, durationMillis: Long): TrainingMetrics {
        require(validFtp(ftp))
        val intensity = if (np != null && ftp != null) np / ftp else null
        // Entire saved monotonic ride duration, including missing-sensor intervals.
        val tss = intensity?.let { durationMillis.coerceAtLeast(0) / 3_600_000.0 * it * it * 100 }
        return TrainingMetrics(np, ftp, intensity, tss)
    }
}
