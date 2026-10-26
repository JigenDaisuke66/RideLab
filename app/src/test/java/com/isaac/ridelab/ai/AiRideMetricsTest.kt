package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.AiRideMetrics
import com.isaac.ridelab.analytics.TrainingAnalytics
import com.isaac.ridelab.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiRideMetricsTest {
    private val ride = RideSession(id = 8, startTimeEpochMillis = 1, durationMillis = 60_000,
        status = StoredRideStatus.COMPLETED, activeSlot = null, analyticsFtpWatts = 130, bodyWeightKg = 65.0)
    private fun sample(second: Int, power: Int? = 100) = RideSample(8, second * 1000L, second * 1000L, power, 80.0, 130, 100)
    @Test fun exposesExistingCalculationsAndRideTimeSnapshots() {
        val samples = (0..60).map { sample(it) }
        val json = JSONObject(AiRideMetrics.calculate(ride, samples))
        val expected = TrainingAnalytics.calculate(100.0, 130, 60_000)
        assertEquals(130, json.getInt("ride_ftp_w")); assertEquals(65.0, json.getDouble("ride_weight_kg"), 0.0)
        assertEquals(100.0, json.getDouble("np_w"), .001)
        assertEquals(expected.intensityFactor!!, json.getDouble("if"), .001)
        assertEquals(expected.tss!!, json.getDouble("tss"), .001)
        assertEquals(100.0, json.getDouble("power_coverage_pct"), .001)
    }
    @Test fun missingSnapshotsAndSensorsAreNotBackfilled() {
        val json = JSONObject(AiRideMetrics.calculate(ride.copy(analyticsFtpWatts = null, bodyWeightKg = null), emptyList()))
        listOf("ride_ftp_w", "ride_weight_kg", "np_w", "if", "tss", "avg_power_w", "avg_hr_bpm", "max_cadence_rpm", "estimated_kcal").forEach {
            assertTrue(it, json.isNull(it))
        }
        assertEquals(0, json.getInt("sample_count"))
    }
    @Test fun zeroIsARealMeasurementAndGapsStayVisible() {
        val zero = JSONObject(AiRideMetrics.calculate(ride, (0..60).map { sample(it, 0) }))
        assertEquals(0.0, zero.getDouble("np_w"), 0.0)
        assertEquals(0.0, zero.getDouble("estimated_kcal"), 0.0)
        val gaps = JSONObject(AiRideMetrics.calculate(ride, listOf(sample(0), sample(10), sample(11))))
        assertTrue(gaps.isNull("np_w")); assertEquals(1, gaps.getInt("gaps_over_3s"))
        assertTrue(gaps.getDouble("power_coverage_pct") < 10)
    }
}
