package com.isaac.ridelab.ble.power

import org.junit.Assert.*
import org.junit.Test

class CrankCadenceCalculatorTest {
    private val calculator = CrankCadenceCalculator()

    @Test fun needsTwoSamplesAndUses1024HzCrankClock() {
        assertNull(calculator.update(CrankRevolutionData(10, 1000), 0))
        assertEquals(90.0, calculator.update(CrankRevolutionData(13, 3048), 2000)!!.value, 0.0)
    }

    @Test fun handlesBothCounterAndEventTimeRollover() {
        calculator.update(CrankRevolutionData(65535, 65000), 0)
        assertEquals(60.0, calculator.update(CrankRevolutionData(0, 488), 1000)!!.value, 0.0)
    }

    @Test fun duplicateEventsDoNotRefreshCadenceAndEventuallyBecomeStale() {
        calculator.update(CrankRevolutionData(1, 1024), 0)
        val reading = calculator.update(CrankRevolutionData(2, 2048), 1000)!!
        val repeated = calculator.update(CrankRevolutionData(2, 2048), 6500)!!
        assertEquals(reading, repeated)
        assertTrue(repeated.isStale(6500))
    }

    @Test fun absentCrankDataAndReconnectResetTheBaseline() {
        calculator.update(CrankRevolutionData(1, 1024), 0)
        calculator.update(CrankRevolutionData(2, 2048), 1000)
        assertNull(calculator.update(null, 1500))
        assertNull(calculator.update(CrankRevolutionData(3, 3072), 2000))
        calculator.reset()
        assertNull(calculator.update(CrankRevolutionData(4, 4096), 3000))
    }

    @Test fun rejectsZeroTimeDeltaCounterResetAndAmbiguousLongGap() {
        calculator.update(CrankRevolutionData(100, 1024), 0)
        assertNull(calculator.update(CrankRevolutionData(101, 1024), 1000))
        assertNull(calculator.update(CrankRevolutionData(0, 2048), 2000))
        assertNull(calculator.update(CrankRevolutionData(1, 3072), 66_000))
    }
}
