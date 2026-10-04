package com.isaac.ridelab.workout

import com.isaac.ridelab.data.model.RideSample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RampTestResultTest {
    private fun rows(seconds: Int = 60, power: Int? = 212) = (0..seconds).map {
        RideSample(1, it * 1000L, it * 1000L, power, null, null, null)
    }
    private fun result(samples: List<RideSample>, ranges: List<EffortRange> = listOf(EffortRange(0, 60000))) =
        RampTestCalculator.calculate(1, samples, ranges)
    @Test fun measuredSixtySecondPowerAndSeventyFivePercent() {
        val r = result(rows())
        assertEquals(212.0, r.best60Watts!!, 0.000001)
        assertEquals(159, r.estimatedFtp)
    }
    @Test fun realZeroValidButOutsideSettingsRangeCannotBeAccepted() {
        val r = result(rows(power = 0))
        assertEquals(0.0, r.best60Watts!!, 0.0)
        assertEquals(0, r.estimatedFtp)
        assertFalse(r.canAccept)
    }
    @Test fun nullAndTimeGapsBreakCoverage() {
        assertNull(result(rows().map { if (it.elapsedMillis == 30000L) it.copy(trainerPowerWatts = null) else it }).estimatedFtp)
        assertNull(result(rows().filter { it.elapsedMillis != 30000L }).estimatedFtp)
    }
    @Test fun shortRideAndWarmupOnlyHaveNoResult() {
        assertNull(result(rows(59)).estimatedFtp)
        assertNull(result(rows(60), emptyList()).estimatedFtp)
        assertNull(result(rows(60), listOf(EffortRange(30000, 60000))).estimatedFtp)
    }
    @Test fun pausedSegmentsCannotBeJoinedAndWarmupExcluded() {
        val all = rows(120, 900).map { if (it.elapsedMillis >= 60000) it.copy(trainerPowerWatts = 212) else it }
        assertEquals(159, result(all, listOf(EffortRange(60000, 120000))).estimatedFtp)
        assertNull(result(all, listOf(EffortRange(0, 30000), EffortRange(60000, 90000))).estimatedFtp)
    }
    @Test fun fractionalPowerEstimateRoundsNearestAndSelectsBestRange() {
        val all = rows(120, 200).map { if (it.elapsedMillis >= 60000) it.copy(trainerPowerWatts = 214) else it }
        val r = result(all, listOf(EffortRange(0, 60000), EffortRange(60000, 120000)))
        assertEquals(161, r.estimatedFtp)
    }
    @Test fun publicationAndRejectNeverUpdateFtp() = runBlocking {
        var ftp: Int? = 150
        var writes = 0
        val review = RampResultReview { ftp = it; writes++ }
        review.publish(result(rows()))
        assertEquals(150, ftp)
        review.keep(); review.accept()
        assertEquals(150, ftp); assertEquals(0, writes)
        assertEquals(RampReviewPhase.KEPT, review.state.value!!.phase)
    }
    @Test fun explicitAcceptanceUsesInjectedExistingSettingsPathExactlyOnce() = runBlocking {
        var ftp: Int? = 150
        val savedSnapshots = listOf(130, 150)
        var writes = 0
        val review = RampResultReview { ftp = it; writes++ }
        review.publish(result(rows())); review.accept(); review.accept()
        assertEquals(159, ftp); assertEquals(1, writes)
        assertEquals(listOf(130, 150), savedSnapshots)
        assertEquals(RampReviewPhase.ACCEPTED, review.state.value!!.phase)
    }
    @Test fun insufficientOrOutOfRangeEstimateCannotOverwriteSetting() = runBlocking {
        var writes = 0
        val review = RampResultReview { writes++ }
        review.publish(result(rows(59))); review.accept()
        review.publish(result(rows(power = 1000))); review.accept()
        assertEquals(0, writes)
    }
    @Test fun failedSaveIsVisibleAndOnlyExplicitRetrySaves() = runBlocking {
        var fail = true
        val review = RampResultReview { if (fail) error("disk") }
        review.publish(result(rows()))
        try { review.accept(); fail("Expected error") } catch (_: IllegalStateException) { }
        assertEquals(RampReviewPhase.FAILED, review.state.value!!.phase)
        fail = false; review.accept()
        assertEquals(RampReviewPhase.ACCEPTED, review.state.value!!.phase)
    }
}
