package com.isaac.ridelab.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ErgTargetValuesTest {
    @Test fun ftmsPendingValuesUseReportedRangeWithoutSendingOrSilentlyChangingDirectInput() {
        val profile = com.isaac.ridelab.ble.fec.TrainerErgProfile(25, 500, 5)
        assertEquals(500, restoredErgTarget(2000, null, profile))
        assertEquals(25, restoredErgTarget(-1, null, profile))
        assertEquals(100, restoredErgTarget(null, null, profile))
        assertEquals(135, parseErgTarget("135", profile))
        assertNull(parseErgTarget("136", profile)); assertNull(parseErgTarget("501", profile))
        assertEquals(105, adjustedErgTarget(100, 5, profile))
        assertEquals(500, adjustedErgTarget(500, 5, profile))
    }
    @Test fun validDirectInput() { assertEquals(135, parseErgTarget("135")); assertEquals(50, parseErgTarget("50")); assertEquals(2000, parseErgTarget("2000")); assertEquals(1350, parseErgTarget("1350")) }
    @Test fun invalidDirectInput() { listOf("", "49", "2001", "-100", "135.5", "1a0", " 100", "9999999999999999").forEach { assertNull(parseErgTarget(it)) } }
    @Test fun savedTargetPrecedesLastSent() { assertEquals(135, restoredErgTarget(135, 100)) }
    @Test fun fallbackUsesLastSentThen100() { assertEquals(150, restoredErgTarget(null, 150)); assertEquals(100, restoredErgTarget(null, null)) }
    @Test fun stalePreferencesClamped() { assertEquals(50, restoredErgTarget(1, null)); assertEquals(2000, restoredErgTarget(9999, null)); assertEquals(1350, restoredErgTarget(1350, null)) }
    @Test fun quickStepsAndBounds() { assertEquals(105, adjustedErgTarget(100,5)); assertEquals(110, adjustedErgTarget(105,5)); assertEquals(105, adjustedErgTarget(110,-5)); assertEquals(2000, adjustedErgTarget(1999,5)); assertEquals(50, adjustedErgTarget(51,-5)) }
}
