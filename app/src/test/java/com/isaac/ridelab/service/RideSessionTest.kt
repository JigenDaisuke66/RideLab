package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.GattFailure
import org.junit.Assert.*
import org.junit.Test

class RideSessionTest {
    @Test fun startPromotesExistingConnectionsWithoutDisconnecting() {
        var disconnects = 0
        val session = RideSession { disconnects++ }
        assertTrue(session.begin())
        assertEquals(RidePhase.STARTING, session.state.value.phase)
        assertFalse(session.begin())
        session.activate(1000)
        assertEquals(RidePhase.ACTIVE, session.state.value.phase)
        assertFalse(session.begin())
        session.activate(5000)
        assertEquals(1000L, session.state.value.startedAtMs)
        assertEquals(0, disconnects)
    }

    @Test fun homeLockRotationUnbindAndViewModelClearCannotDisconnectActiveRide() {
        var disconnects = 0
        val session = RideSession { disconnects++ }
        session.begin()
        session.uiHidden() // Also protect the brief FGS promotion window.
        session.activate(1000)
        repeat(10) { session.uiHidden() }
        assertEquals(0, disconnects)
        assertEquals(RidePhase.ACTIVE, session.state.value.phase)
    }

    @Test fun previewConnectionsCloseWhenUiDisappears() {
        var disconnects = 0
        RideSession { disconnects++ }.uiHidden()
        assertEquals(1, disconnects)
    }

    @Test fun elapsedIncludesTwentyFiveMinutesWithoutAnyUiTicks() {
        val session = RideSession {}
        session.begin(); session.activate(1000)
        session.uiHidden()
        assertEquals(25 * 60 * 1000L, session.state.value.elapsedMs(1000 + 25 * 60 * 1000L))
        assertEquals(0, session.state.value.elapsedMs(999))
    }

    @Test fun explicitEndRetainsConnectionsAndResetsTimer() {
        lateinit var session: RideSession
        var disconnects = 0
        session = RideSession {
            assertEquals(RidePhase.STOPPING, session.state.value.phase)
            assertTrue(session.keepsConnections) // STOPPING preserves BLE until storage finalization.
            disconnects++
        }
        session.begin(); session.activate(1000); session.end()
        assertEquals(0, disconnects)
        assertEquals(RidePhase.IDLE, session.state.value.phase)
        assertNull(session.state.value.startedAtMs)
        session.uiHidden()
        assertEquals(0, disconnects)
        assertTrue(session.begin())
    }

    @Test fun startupFailurePublishesTypedErrorAndClosesConnections() {
        var disconnects = 0
        val session = RideSession { disconnects++ }
        session.begin(); session.fail(RideError.START_FAILED)
        assertEquals(RidePhase.ERROR, session.state.value.phase)
        assertEquals(RideError.START_FAILED, session.state.value.error)
        assertFalse(session.keepsConnections)
        assertEquals(1, disconnects)
        session.activate(1000)
        assertEquals(RidePhase.ERROR, session.state.value.phase)
        assertTrue(session.begin())
    }

    @Test fun recoveryHasOneAttemptAndDoesNotResetOnSuccessfulConnection() {
        val budget = ReconnectBudget()
        assertTrue(budget.take(true, GattFailure.CONNECTION_LOST))
        repeat(10) { assertFalse(budget.take(true, GattFailure.CONNECTION_LOST)) }
        assertFalse(budget.take(true, GattFailure.CONNECT_FAILED))
        budget.reset() // Explicit new ride or manual selection only.
        assertTrue(budget.take(true, GattFailure.TIMEOUT))
    }

    @Test fun inactiveAndNonRetryableFailuresDoNotConsumeBudget() {
        val budget = ReconnectBudget()
        assertFalse(budget.take(false, GattFailure.CONNECTION_LOST))
        assertFalse(budget.take(true, GattFailure.PERMISSION_REQUIRED))
        assertFalse(budget.take(true, GattFailure.CCCD_MISSING))
        assertFalse(budget.take(true, null))
        assertTrue(budget.take(true, GattFailure.CONNECTION_LOST))
    }

    @Test fun trainerAndHeartRateRetryBudgetsAreIndependent() {
        val trainer = ReconnectBudget()
        val heartRate = ReconnectBudget()
        assertTrue(trainer.take(true, GattFailure.CONNECTION_LOST))
        assertFalse(trainer.take(true, GattFailure.CONNECTION_LOST))
        assertTrue(heartRate.take(true, GattFailure.CONNECTION_LOST))
    }

    @Test fun stoppingRetainsConnectionsAndSessionIdUntilFinalSampleCommits() {
        var disconnects = 0
        val session = RideSession { disconnects++ }
        session.begin(); session.activate(100, 42)
        assertTrue(session.beginStop())
        assertFalse(session.beginStop())
        assertFalse(session.begin())
        session.uiHidden()
        assertEquals(0, disconnects)
        assertEquals(42L, session.state.value.sessionId)
        session.end()
        assertEquals(0, disconnects)
    }

    @Test fun pauseFreezesClockAndPreservesConnectionsAcrossBackgroundAndResume() {
        var disconnects = 0
        val session = RideSession { disconnects++ }
        session.begin(); session.activate(1000, 42)
        assertTrue(session.pause(11_000))
        assertFalse(session.pause(12_000))
        assertFalse(session.begin())
        session.uiHidden()
        assertEquals(10_000L, session.state.value.elapsedMs(311_000))
        assertEquals(11_000L, session.state.value.recordingClock(311_000))
        assertTrue(session.resume(311_000))
        assertFalse(session.resume(312_000))
        assertEquals(11_000L, session.state.value.elapsedMs(312_000))
        assertEquals(42L, session.state.value.sessionId)
        assertEquals(0, disconnects)
        assertTrue(session.pause(313_000))
        assertTrue(session.beginStop())
        assertEquals(12_000L, session.state.value.elapsedMs(999_000))
        session.end(); session.uiHidden()
        assertEquals(0, disconnects)
        session.releasePreview(); session.uiHidden()
        assertEquals(1, disconnects)
    }
}
