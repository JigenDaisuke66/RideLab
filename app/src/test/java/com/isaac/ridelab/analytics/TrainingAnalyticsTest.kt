package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.RideSample
import org.junit.Assert.*
import org.junit.Test

class TrainingAnalyticsTest {
    private fun samples(seconds: Int, power: (Int) -> Int? = { 100 }) = (0 until seconds).map {
        RideSample(1, it * 1000L, it * 1000L, power(it), null, null, null)
    }
    @Test fun constantPower() { assertEquals(100.0, TrainingAnalytics.normalizedPower(samples(60), 60000)!!, 0.0001) }
    @Test fun variablePower() {
        val rows = samples(120) { if (it < 60) 100 else 300 }
        assertTrue(TrainingAnalytics.normalizedPower(rows, 120000)!! > 200)
    }
    @Test fun realZeros() { assertEquals(0.0, TrainingAnalytics.normalizedPower(samples(60) { 0 }, 60000)!!, 0.0) }
    @Test fun nullsInvalidateWindows() {
        assertNull(TrainingAnalytics.normalizedPower(samples(59) { if (it == 29) null else 100 }, 59000))
        assertEquals(100.0, TrainingAnalytics.normalizedPower(samples(90) { if (it == 40) null else 100 }, 90000)!!, 0.0001)
    }
    @Test fun timestampGapsBreakContinuity() {
        val rows = samples(60).filter { it.elapsedMillis !in 29000L..35000L }
        assertNull(TrainingAnalytics.normalizedPower(rows, 60000))
    }
    @Test fun shortRide() {
        assertNull(TrainingAnalytics.normalizedPower(samples(30), 29999))
        assertNotNull(TrainingAnalytics.normalizedPower(samples(30), 30000))
        assertNull(TrainingAnalytics.normalizedPower(emptyList(), 3600000))
    }
    @Test fun intensityAndStressUseDurationNotRows() {
        val result = TrainingAnalytics.calculate(100.0, 200, 3600000)
        assertEquals(0.5, result.intensityFactor!!, 0.0)
        assertEquals(25.0, result.tss!!, 0.0)
        assertEquals(100.0, TrainingAnalytics.calculate(200.0, 200, 3600000).tss!!, 0.0)
    }
    @Test fun ftpUnsetAndNpUnavailable() {
        val result = TrainingAnalytics.calculate(100.0, null, 3600000)
        assertEquals(100.0, result.np!!, 0.0); assertNull(result.intensityFactor); assertNull(result.tss)
        assertNull(TrainingAnalytics.calculate(null, 200, 3600000).tss)
    }
    @Test fun ftpValidation() {
        for (value in listOf(null, 50, 200, 500)) assertTrue(TrainingAnalytics.validFtp(value))
        for (value in listOf(-1, 0, 49, 501)) assertFalse(TrainingAnalytics.validFtp(value))
    }
    @Test fun orderDuplicatesAndMissingSeconds() {
        val rows = samples(60)
        assertEquals(TrainingAnalytics.normalizedPower(rows, 60000), TrainingAnalytics.normalizedPower(rows.reversed(), 60000))
        assertEquals(100.0, TrainingAnalytics.normalizedPower(rows + rows, 60000)!!, 0.0)
        assertNull(TrainingAnalytics.normalizedPower(samples(30).filter { it.elapsedMillis != 15000L }, 30000))
    }
}
