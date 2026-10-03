package com.isaac.ridelab.analytics

import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CyclingEnergyTest {
    @Test fun energyConversionUsesEfficiencyNotDirectKjConversion() {
        assertEquals(1.0, CyclingEnergyCalculator.estimate(4.184 * 0.24)!!.kcal, 0.000001)
        val estimate = CyclingEnergyCalculator.estimate(360.0)!!
        assertEquals(360.0 / (4.184 * 0.24), estimate.kcal, 0.000001)
        assertTrue(estimate.kcal > 350 && estimate.kcal < 360)
    }

    @Test fun sensitivityRangeOrderedAndUsesExplicitAssumptions() {
        val e = CyclingEnergyCalculator.estimate(360.0)!!
        assertEquals(360.0 / (4.184 * 0.25), e.lowerKcal, 0.000001)
        assertEquals(360.0 / (4.184 * 0.20), e.upperKcal, 0.000001)
        assertTrue(e.lowerKcal <= e.kcal && e.kcal <= e.upperKcal)
    }

    @Test fun absentAndInvalidWorkDoNotInventCalories() {
        listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNull(CyclingEnergyCalculator.estimate(it))
        }
    }

    @Test fun realZeroHasZeroEstimatedOutputWithoutInventedRestingBurn() {
        val e = CyclingEnergyCalculator.estimate(0.0)!!
        assertEquals(0.0, e.kcal, 0.0)
        assertEquals(0.0, e.lowerKcal, 0.0)
        assertEquals(0.0, e.upperKcal, 0.0)
        assertTrue(e.foodEquivalents.all { it.portions == 0.0 })
    }

    @Test fun foodComparisonsAreIndependentAndFractional() {
        val e = CyclingEnergyCalculator.estimate(105.0 * 4.184 * 0.24)!!
        assertEquals(EnergyFood.entries.toList(), e.foodEquivalents.map { it.food })
        assertEquals(1.0, e.foodEquivalents.first { it.food == EnergyFood.BANANA }.portions, 0.000001)
        assertEquals(105.0 / 78, e.foodEquivalents.first { it.food == EnergyFood.BOILED_EGG }.portions, 0.000001)
        assertEquals(105.0 / 242, e.foodEquivalents.first { it.food == EnergyFood.COOKED_RICE_CUP }.portions, 0.000001)
    }

    private fun row(time: Long, power: Int?) = RideSample(1, time, time, power, null, null, null)
    private fun insights(rows: List<RideSample>, duration: Long) = RideInsightsCalculator.calculate(rows,
        RideSession(startTimeEpochMillis = 0, durationMillis = duration, status = StoredRideStatus.COMPLETED))

    @Test fun constant100WattsForOneHourProducesApproximately359Kcal() {
        val r = insights((0..3600).map { row(it * 1000L, 100) }, 3600000)
        assertEquals(358.5, r.energyEstimate!!.kcal, 0.1)
        assertEquals(100.0, r.powerCoverage.percent!!, 0.0)
    }

    @Test fun missingPowerAndLargeGapsAreNotEstimated() {
        val r = insights(listOf(row(0, 100), row(1000, null), row(2000, 100), row(10000, 100)), 20000)
        assertEquals(0.1 / (4.184 * 0.24), r.energyEstimate!!.kcal, 0.000001)
        assertEquals(5.0, r.powerCoverage.percent!!, 0.0)
    }

    @Test fun noCoveredPowerProducesNoCaloriesOrFoods() {
        assertNull(insights(emptyList(), 1000).energyEstimate)
        assertNull(insights(listOf(row(0, null), row(1000, null)), 1000).energyEstimate)
        assertNull(insights(listOf(row(0, 100)), 1000).energyEstimate)
    }
}
