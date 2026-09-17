package com.isaac.ridelab.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ErgTargetValuesTest {
    @Test fun validDirectInput() { assertEquals(135, parseErgTarget("135")); assertEquals(50, parseErgTarget("50")); assertEquals(200, parseErgTarget("200")) }
    @Test fun invalidDirectInput() { listOf("", "49", "201", "-100", "135.5", "1a0", " 100", "9999999999999999").forEach { assertNull(parseErgTarget(it)) } }
    @Test fun savedTargetPrecedesLastSent() { assertEquals(135, restoredErgTarget(135, 100)) }
    @Test fun fallbackUsesLastSentThen100() { assertEquals(150, restoredErgTarget(null, 150)); assertEquals(100, restoredErgTarget(null, null)) }
    @Test fun stalePreferencesClamped() { assertEquals(50, restoredErgTarget(1, null)); assertEquals(200, restoredErgTarget(999, null)) }
    @Test fun quickStepsAndBounds() { assertEquals(105, adjustedErgTarget(100,5)); assertEquals(110, adjustedErgTarget(105,5)); assertEquals(105, adjustedErgTarget(110,-5)); assertEquals(200, adjustedErgTarget(199,5)); assertEquals(50, adjustedErgTarget(51,-5)) }
}
