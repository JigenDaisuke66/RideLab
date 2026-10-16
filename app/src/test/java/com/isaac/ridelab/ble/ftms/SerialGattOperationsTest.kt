package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.control.SerialGattOperations
import org.junit.Assert.*
import org.junit.Test

class SerialGattOperationsTest {
    @Test fun readsCccdAndControlWritesAreSerialized() {
        val started = mutableListOf<String>(); val completed = mutableListOf<String>()
        val queue = SerialGattOperations({}, {}, {})
        for (key in listOf("feature", "range", "indicationCccd", "statusCccd", "requestControl"))
            queue.enqueue(key, { started.add(key); true }) { _, _ -> completed.add(key) }
        assertEquals(listOf("feature"), started)
        assertFalse(queue.complete("wrong", 0))
        queue.complete("feature", 0); assertEquals(listOf("feature", "range"), started)
        for (key in listOf("range", "indicationCccd", "statusCccd", "requestControl")) queue.complete(key, 0)
        assertEquals(started, completed); assertTrue(queue.idle)
    }
    @Test fun timeoutOrDisconnectDiscardsQueueAndLateCallbacks() {
        var started = 0; var completed = 0; var timeouts = 0
        val queue = SerialGattOperations({}, {}, { timeouts++ })
        repeat(2) { queue.enqueue(it, { started++; true }) { _, _ -> completed++ } }
        queue.timeout(); queue.complete(0, 0); queue.enqueue(2, { started++; true }) { _, _ -> completed++ }
        assertEquals(1, started); assertEquals(0, completed); assertEquals(1, timeouts)
    }
    @Test fun dispatchFailureIsReportedAndDoesNotBlockFollowingOperation() {
        var code: Int? = null
        val queue = SerialGattOperations({}, {}, {})
        queue.enqueue(1, { false }) { status, _ -> code = status }
        assertEquals(-1, code); assertTrue(queue.idle)
    }
}
