package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class RideInsightsTest {
    private fun row(time: Long, watts: Int? = 100, cadence: Double? = 80.0,
        hr: Int? = 140, speed: Double? = null) = RideSample(1, time, time, watts, cadence, hr, null, speedKph = speed)
    private fun result(rows: List<RideSample>, duration: Long = 60000, weight: Double? = null) =
        RideInsightsCalculator.calculate(rows, RideSession(startTimeEpochMillis = 0,
            durationMillis = duration, status = StoredRideStatus.COMPLETED, bodyWeightKg = weight))

    @Test fun constantPowerWorkAndFullCoverage() {
        val r = result((0..60).map { row(it * 1000L) })
        assertEquals(6.0, r.workKilojoules!!, 0.000001)
        assertEquals(100.0, r.powerCoverage.percent!!, 0.0)
        assertEquals(60000L, r.pedalingMillis)
        assertEquals(0L, r.zeroPowerMillis)
    }

    @Test fun realZerosAreValidNotMissing() {
        val r = result(listOf(row(0, 0, 0.0, speed = 0.0), row(1000)), 1000)
        assertEquals(0.0, r.workKilojoules!!, 0.0)
        assertEquals(0.0, r.recordedDistanceKm!!, 0.0)
        assertEquals(1000L, r.zeroPowerMillis)
        assertEquals(0L, r.pedalingMillis)
        assertEquals(100.0, r.cadenceBands!![0].percent, 0.0)
    }

    @Test fun missingSensorsRemainMissingIndependently() {
        val r = result(listOf(row(0, null, 80.0, null), row(1000)), 1000)
        assertNull(r.workKilojoules)
        assertNull(r.zeroPowerMillis)
        assertNull(r.recordedDistanceKm)
        assertEquals(0L, r.powerCoverage.millis)
        assertEquals(0L, r.heartRateCoverage.millis)
        assertEquals(1000L, r.cadenceCoverage.millis)
    }

    @Test fun missingPowerIsNotConvertedToZero() {
        val r = result(listOf(row(0, 100), row(1000, null), row(2000, 0), row(3000)), 3000)
        assertEquals(0.1, r.workKilojoules!!, 0.000001)
        assertEquals(1000L, r.zeroPowerMillis)
        assertEquals(2000L, r.powerCoverage.millis)
    }

    @Test fun gapsAreNotBridgedAndFinalTailIsNotAssumed() {
        val r = result(listOf(row(0), row(1000), row(10000), row(11000)), 20000)
        assertEquals(0.2, r.workKilojoules!!, 0.000001)
        assertEquals(2000L, r.powerCoverage.millis)
        assertEquals(10.0, r.powerCoverage.percent!!, 0.0)
    }

    @Test fun jitterIntervalsAreTimeWeighted() {
        val r = result(listOf(row(0, 200), row(500, 100), row(2000, 300), row(3501)), 3501)
        assertEquals(0.25, r.workKilojoules!!, 0.000001)
        assertEquals(2000L, r.powerCoverage.millis)
    }

    @Test fun speedDistanceUsesOnlyRecordedIntervals() {
        val r = result((0..10).map { row(it * 1000L, speed = 36.0) }, 10000)
        assertEquals(0.1, r.recordedDistanceKm!!, 0.000001)
        assertEquals(10000L, r.speedCoverage.millis)
    }

    @Test fun cadenceBandsHaveExactBoundariesAndValidTimeDenominator() {
        listOf(0.0, 59.9, 60.0, 79.9, 80.0, 99.9, 100.0).zip(listOf(0, 1, 2, 2, 3, 3, 4)).forEach { (rpm, index) ->
            assertEquals(index, RideInsightsCalculator.cadenceBand(rpm))
        }
        val r = result(listOf(row(0, cadence = 0.0), row(1000, cadence = 60.0),
            row(2000, cadence = null), row(3000, cadence = 100.0), row(4000)), 4000)
        assertEquals(3000L, r.cadenceCoverage.millis)
        assertEquals(2000L, r.pedalingMillis)
        assertEquals(100.0, r.cadenceBands!!.sumOf { it.percent }, 0.000001)
        assertEquals(1000L, r.cadenceBands[4].millis)
    }

    @Test fun invalidNegativeAndNonFiniteReadingsAreExcluded() {
        val r = result(listOf(row(0, -1, Double.NaN, 0, Double.POSITIVE_INFINITY),
            row(1000, null, -1.0, -1, -1.0), row(2000)), 2000)
        assertNull(r.workKilojoules)
        assertNull(r.averageWattsPerKg)
        assertNull(r.cadenceBands)
        assertNull(r.recordedDistanceKm)
        assertEquals(0L, r.heartRateCoverage.millis)
    }

    @Test fun emptySingleAndZeroDurationDoNotInventEnergy() {
        assertNull(result(emptyList()).workKilojoules)
        assertNull(result(listOf(row(0))).workKilojoules)
        assertNull(result(listOf(row(0)), 0).powerCoverage.percent)
        assertNull(result(emptyList()).cadenceBands)
    }

    @Test fun orderingDuplicatesAndOutOfRangeSamplesCannotDoubleCount() {
        val rows = listOf(row(-1000, 9999), row(1000), row(0, 9999), row(0, 100), row(2000, 9999))
        val r = result(rows, 1000)
        assertEquals(0.1, r.workKilojoules!!, 0.000001)
        assertEquals(1000L, r.powerCoverage.millis)
        assertEquals(result(listOf(row(0), row(1000)), 1000), r)
    }

    @Test fun averageWattsPerKgUsesHistoricalWeightAndNullAwareMean() {
        val rows = listOf(row(0, 0), row(1000, null), row(2000, 200))
        assertEquals(2.0, result(rows, 2000, 50.0).averageWattsPerKg!!, 0.0)
        assertNull(result(rows, 2000).averageWattsPerKg)
    }
}
