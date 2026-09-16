package com.isaac.ridelab.ui.chart

import com.isaac.ridelab.data.model.RideSample

data class ChartPoint(val elapsedMillis: Long, val value: Double)
data class ChartSeries(val segments: List<List<ChartPoint>>)
data class RideCharts(val power: ChartSeries, val heartRate: ChartSeries, val cadence: ChartSeries, val endMillis: Long)

/** Split before reducing: neither missing measurements nor >3-second recording gaps are bridged.
 * Each time bucket retains its first, minimum, maximum and last point, in original order.
 * Rendering cost is proportional to buckets + gap boundaries, rather than raw sample count.
 */
fun buildRideCharts(samples: List<RideSample>, bucketCount: Int = 256): RideCharts {
    require(bucketCount > 0)
    val ordered = samples.sortedBy { it.elapsedMillis }
    val end = ordered.lastOrNull()?.elapsedMillis?.coerceAtLeast(0) ?: 0
    val bucketWidth = (end / bucketCount + 1).coerceAtLeast(1)
    fun series(value: (RideSample) -> Double?): ChartSeries {
        val segments = mutableListOf<List<ChartPoint>>()
        var current = mutableListOf<ChartPoint>()
        var previous: Long? = null
        fun flush() {
            if (current.isEmpty()) return
            val reduced = current.groupBy { it.elapsedMillis / bucketWidth }.values.flatMap { bucket ->
                listOf(bucket.first(), bucket.minBy { it.value }, bucket.maxBy { it.value }, bucket.last())
                    .distinct().sortedBy { it.elapsedMillis }
            }
            segments.add(reduced)
            current = mutableListOf()
        }
        for (sample in ordered) {
            if (previous != null && sample.elapsedMillis - previous!! > 3_000) flush()
            val measurement = value(sample)
            if (measurement == null || !measurement.isFinite()) flush()
            else current.add(ChartPoint(sample.elapsedMillis, measurement))
            previous = sample.elapsedMillis
        }
        flush()
        return ChartSeries(segments)
    }
    return RideCharts(series { it.trainerPowerWatts?.toDouble() },
        series { it.heartRateBpm?.toDouble() }, series { it.cadenceRpm }, end)
}
