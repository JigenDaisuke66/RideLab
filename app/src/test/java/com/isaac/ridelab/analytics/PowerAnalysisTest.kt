package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PowerAnalysisTest {
    private fun sample(time: Long, watts: Int? = 100) = RideSample(1, time, time, watts, null, null, null)
    private fun session(duration: Long = 5000, ftp: Int? = 100, weight: Double? = null) = RideSession(id=10, startTimeEpochMillis=10000, durationMillis=duration, status=StoredRideStatus.COMPLETED, analyticsFtpWatts=ftp, bodyWeightKg=weight)
    private fun result(rows: List<RideSample>, duration: Long = 5000) = PowerAnalysisCalculator.calculate(rows, session(duration))
    private fun regular(seconds: Int, watts: Int? = 100) = (0..seconds).map { sample(it*1000L, watts) }
    @Test fun exactDurationAndNoTailExtrapolation() {
        assertEquals(100.0, result(regular(5)).efforts[0].watts!!, 0.0)
        assertNull(result(regular(4)).efforts[0].watts)
    }
    @Test fun zerosAreValid() { assertEquals(0.0, result(regular(5,0)).efforts[0].watts!!,0.0) }
    @Test fun nullBreaksWindow() { assertNull(result(regular(5).map { if(it.elapsedMillis==2000L) it.copy(trainerPowerWatts=null) else it }).efforts[0].watts) }
    @Test fun timeGapsBreakWindow() { assertNull(result(regular(6).filter { it.elapsedMillis != 3000L },6000).efforts[0].watts) }
    @Test fun shorterRideUnavailable() { assertNull(result(regular(5),4999).efforts[0].watts) }
    @Test fun correctMaximumWindow() {
        val rows=(0..10).map { sample(it*1000L, if(it in 3..7) 300 else 100) }
        assertEquals(300.0, result(rows,10000).efforts[0].watts!!,0.0)
    }
    @Test fun everyFixedDuration() {
        val efforts=result(regular(3600),3600000).efforts
        assertEquals(listOf(5,15,30,60,300,1200,3600),efforts.map { it.seconds })
        efforts.forEach { assertEquals(100.0,it.watts!!,0.000001) }
    }
    @Test fun irregularIntervalsAreTimeWeighted() {
        val rows=listOf(sample(0,200),sample(500,100),sample(1500,100),sample(2500,100),sample(3500,100),sample(4500,100),sample(5000,0))
        assertEquals(110.0,result(rows).efforts[0].watts!!,0.000001)
        assertEquals(result(rows), result(rows.reversed()))
    }
    @Test fun optimumCanEndBetweenBoundaries() {
        val rows=listOf(sample(0,100),sample(1000,100),sample(2000,100),sample(3000,100),sample(4000,100),sample(5000,200),sample(5500,0),sample(6500,0))
        assertEquals(110.0,result(rows,6500).efforts[0].watts!!,0.000001)
    }
    @Test fun prNewEqualBelowAndFirst() {
        val complete=StoredRideStatus.COMPLETED
        assertEquals(PrStatus.NEW,PowerAnalysisCalculator.compare(221.0,215.0,complete).status)
        assertEquals(PrStatus.MATCH,PowerAnalysisCalculator.compare(215.0,215.0,complete).status)
        assertEquals(PrStatus.BELOW,PowerAnalysisCalculator.compare(200.0,215.0,complete).status)
        assertEquals(PrStatus.FIRST,PowerAnalysisCalculator.compare(200.0,null,complete).status)
        assertEquals(PrStatus.UNAVAILABLE,PowerAnalysisCalculator.compare(null,215.0,complete).status)
    }
    @Test fun futureAndNoncompletedRidesExcluded() {
        val current=session()
        assertTrue(PowerAnalysisCalculator.eligiblePrevious(current,current.copy(id=9)))
        assertFalse(PowerAnalysisCalculator.eligiblePrevious(current,current.copy(id=11)))
        assertFalse(PowerAnalysisCalculator.eligiblePrevious(current,current.copy(id=1,startTimeEpochMillis=11000)))
        for(status in listOf(StoredRideStatus.ACTIVE,StoredRideStatus.ABORTED)) {
            assertFalse(PowerAnalysisCalculator.eligiblePrevious(current,current.copy(id=1,status=status)))
            assertEquals(PrStatus.UNAVAILABLE,PowerAnalysisCalculator.compare(200.0,null,status).status)
        }
    }
    @Test fun weightDecimalNullAndValidation() {
        assertEquals(1.78,BodyWeight.wattsPerKg(178.0,100.0)!!,0.000001)
        assertEquals(2.0,BodyWeight.wattsPerKg(201.0,100.5)!!,0.000001)
        assertNull(BodyWeight.wattsPerKg(100.0,null))
        assertNull(BodyWeight.wattsPerKg(null,100.0))
        assertEquals(100.5,BodyWeight.parse("100,5")!!,0.0)
        listOf(0.0,19.9,300.1,Double.NaN,Double.POSITIVE_INFINITY).forEach { assertFalse(BodyWeight.valid(it)) }
        assertTrue(BodyWeight.valid(null))
    }
    @Test fun zoneBoundaries() {
        val inputs=listOf(0.0,54.99,55.0,75.0,75.01,90.0,90.01,105.0,105.01,120.0,120.01,150.0,150.01)
        assertEquals(listOf(0,0,1,1,2,2,3,3,4,4,5,5,6), inputs.map { PowerAnalysisCalculator.zone(it,100) })
    }
    @Test fun zoneElapsedAccumulationExcludesNullAndGaps() {
        val rows=listOf(sample(0,0),sample(500,60),sample(1500,null),sample(2500,100),sample(5000,160),sample(6000))
        val zones=PowerAnalysisCalculator.calculate(rows,session(6000)).zones!!
        assertEquals(500L,zones[0].millis); assertEquals(1000L,zones[1].millis); assertEquals(1000L,zones[6].millis)
        assertEquals(2500L,zones.sumOf { it.millis }); assertEquals(100.0,zones.sumOf { it.percent },0.000001)
        assertEquals(20.0,zones[0].percent,0.000001)
    }
    @Test fun noFtpOrPowerHasNoZonesButEffortsRemain() {
        val data=PowerAnalysisCalculator.calculate(regular(5),session(ftp=null))
        assertNull(data.zones); assertEquals(100.0,data.efforts[0].watts!!,0.0)
        assertNull(PowerAnalysisCalculator.calculate(regular(5,null),session()).zones)
    }
}
