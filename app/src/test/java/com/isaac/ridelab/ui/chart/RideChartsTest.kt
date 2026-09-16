package com.isaac.ridelab.ui.chart

import com.isaac.ridelab.data.model.RideSample
import org.junit.Assert.*
import org.junit.Test

class RideChartsTest {
    private fun sample(time: Long, power: Int? = 100, hr: Int? = 130, cadence: Double? = 80.0) =
        RideSample(1, 100000 + time, time, power, cadence, hr, null)

    @Test fun convertsAllMetricsInElapsedOrder() {
        val data = buildRideCharts(listOf(sample(2000, 200, 140, 90.0), sample(0, 100, 120, 70.0)))
        assertEquals(listOf(0L, 2000L), data.power.segments.single().map { it.elapsedMillis })
        assertEquals(listOf(100.0, 200.0), data.power.segments.single().map { it.value })
        assertEquals(listOf(120.0, 140.0), data.heartRate.segments.single().map { it.value })
        assertEquals(listOf(70.0, 90.0), data.cadence.segments.single().map { it.value })
        assertEquals(2000L, data.endMillis)
    }

    @Test fun nullBreaksOnlyItsMetricAndZeroRemainsValid() {
        val data = buildRideCharts(listOf(sample(0, 0), sample(1000, null), sample(2000, 200)))
        assertEquals(2, data.power.segments.size)
        assertEquals(0.0, data.power.segments.first().single().value, 0.0)
        assertEquals(1, data.heartRate.segments.size)
        assertEquals(1, data.cadence.segments.size)
    }

    @Test fun elapsedGapsOverThreeSecondsBreakAllSegments() {
        val data = buildRideCharts(listOf(sample(0), sample(3000), sample(6001)))
        for (series in listOf(data.power, data.heartRate, data.cadence)) {
            assertEquals(listOf(2, 1), series.segments.map { it.size })
        }
    }

    @Test fun missingValuesAndEmptyRideHaveNoPoints() {
        val data = buildRideCharts(listOf(sample(0, null, null, null)))
        assertTrue(data.power.segments.isEmpty())
        assertTrue(data.heartRate.segments.isEmpty())
        assertTrue(data.cadence.segments.isEmpty())
        assertEquals(0L, buildRideCharts(emptyList()).endMillis)
    }

    @Test fun downsamplingIsDeterministicAndKeepsPeaksAndEndpoints() {
        val samples = (0..9999).map { sample(it * 1000L, if (it == 5050) 900 else if (it == 5051) 0 else 100) }
        val data = buildRideCharts(samples, bucketCount = 64)
        val points = data.power.segments.single()
        assertTrue(points.size <= 64 * 4)
        assertEquals(0L, points.first().elapsedMillis)
        assertEquals(9999000L, points.last().elapsedMillis)
        assertTrue(points.any { it.value == 900.0 })
        assertTrue(points.any { it.value == 0.0 })
        assertEquals(data, buildRideCharts(samples.reversed(), bucketCount = 64))
    }

    @Test fun downsamplingCannotBridgeNullsWithinSameBucket() {
        val data = buildRideCharts(listOf(sample(0), sample(1000, null), sample(2000)), bucketCount = 1)
        assertEquals(2, data.power.segments.size)
        assertEquals(listOf(0L, 2000L), data.power.segments.flatten().map { it.elapsedMillis })
    }

    @Test fun wallClockChangesDoNotDistortElapsedAxis() {
        val data = buildRideCharts(listOf(sample(0).copy(timestampEpochMillis = 100000),
            sample(1000).copy(timestampEpochMillis = 1)))
        assertEquals(1, data.power.segments.size)
        assertEquals(1000L, data.endMillis)
    }
}
