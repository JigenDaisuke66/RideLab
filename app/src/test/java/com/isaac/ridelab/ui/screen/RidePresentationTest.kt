package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.data.model.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class RidePresentationTest {
    @Test fun pendingTargetsStayWithinVerifiedRange() {
        assertEquals(50, adjustedErgTarget(50, -5))
        assertEquals(200, adjustedErgTarget(200, 5))
        assertEquals(105, adjustedErgTarget(100, 5))
        assertEquals(95, adjustedErgTarget(100, -5))
    }
    @Test fun connectingIsNotShownAsReadyToStart() {
        for (state in listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, ConnectionState.DISCOVERING, ConnectionState.FAILED)) assertFalse(state.readyForRide())
        for (state in listOf(ConnectionState.CONNECTED, ConnectionState.SUBSCRIBING, ConnectionState.STREAMING)) assertTrue(state.readyForRide())
    }
}
