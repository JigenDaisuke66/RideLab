package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.GattFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RidePhase { IDLE, STARTING, ACTIVE, PAUSED, STOPPING, ERROR }
enum class RideError { START_FAILED, BLUETOOTH_PERMISSION, NOTIFICATIONS_DISABLED, SERVICE_LOST, RECORDING_FAILED }
data class RideState(val phase: RidePhase = RidePhase.IDLE, val startedAtMs: Long? = null, val error: RideError? = null,
    val sessionId: Long? = null, val pausedAtMs: Long? = null, val pausedDurationMs: Long = 0) {
    val ongoing get() = phase == RidePhase.ACTIVE || phase == RidePhase.PAUSED
    fun elapsedMs(nowMs: Long): Long = startedAtMs?.let {
        ((pausedAtMs ?: nowMs) - it - pausedDurationMs).coerceAtLeast(0)
    } ?: 0
    fun recordingClock(nowMs: Long): Long = (startedAtMs ?: nowMs) + elapsedMs(nowMs)
}

/** Lifecycle policy shared by the service and unit tests. No Activity owns this object. */
class RideSession(private val disconnectAll: () -> Unit) {
    private val mutableState = MutableStateFlow(RideState())
    val state = mutableState.asStateFlow()
    private var retainPreview = false
    val keepsConnections get() = retainPreview || state.value.phase in setOf(RidePhase.STARTING, RidePhase.ACTIVE, RidePhase.PAUSED, RidePhase.STOPPING)

    fun begin(): Boolean {
        if (state.value.phase !in setOf(RidePhase.IDLE, RidePhase.ERROR)) return false
        mutableState.value = RideState(RidePhase.STARTING)
        return true
    }

    fun activate(nowMs: Long, sessionId: Long? = null) {
        if (state.value.phase == RidePhase.STARTING) mutableState.value = RideState(RidePhase.ACTIVE, nowMs, sessionId = sessionId)
    }

    fun pause(nowMs: Long): Boolean {
        if (state.value.phase != RidePhase.ACTIVE) return false
        mutableState.value = state.value.copy(phase = RidePhase.PAUSED, pausedAtMs = nowMs)
        return true
    }

    fun resume(nowMs: Long): Boolean {
        val current = state.value
        if (current.phase != RidePhase.PAUSED) return false
        mutableState.value = current.copy(phase = RidePhase.ACTIVE, pausedAtMs = null,
            pausedDurationMs = current.pausedDurationMs + (nowMs - current.pausedAtMs!!).coerceAtLeast(0))
        return true
    }

    fun beginStop(): Boolean {
        if (state.value.phase !in setOf(RidePhase.STARTING, RidePhase.ACTIVE, RidePhase.PAUSED)) return false
        mutableState.value = state.value.copy(phase = RidePhase.STOPPING)
        return true
    }

    fun uiHidden() { if (!keepsConnections) disconnectAll() }

    fun end() {
        retainPreview = true
        mutableState.value = RideState()
    }

    fun releasePreview() { retainPreview = false }

    fun fail(error: RideError) {
        retainPreview = false
        mutableState.value = RideState(RidePhase.ERROR, error = error)
        disconnectAll()
    }
}

/** One automatic attempt per role per ride. Only explicit selection/new ride resets it. */
class ReconnectBudget {
    private var used = false
    fun reset() { used = false }
    fun take(active: Boolean, failure: GattFailure?): Boolean {
        if (!active || used || failure !in setOf(GattFailure.CONNECTION_LOST, GattFailure.TIMEOUT,
                GattFailure.CONNECT_FAILED, GattFailure.BLUETOOTH_UNAVAILABLE)) return false
        used = true
        return true
    }
}
