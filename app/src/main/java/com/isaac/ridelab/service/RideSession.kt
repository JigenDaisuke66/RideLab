package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.GattFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RidePhase { IDLE, STARTING, ACTIVE, STOPPING, ERROR }
enum class RideError { START_FAILED, BLUETOOTH_PERMISSION, NOTIFICATIONS_DISABLED, SERVICE_LOST }
data class RideState(val phase: RidePhase = RidePhase.IDLE, val startedAtMs: Long? = null, val error: RideError? = null) {
    fun elapsedMs(nowMs: Long): Long = startedAtMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
}

/** Lifecycle policy shared by the service and unit tests. No Activity owns this object. */
class RideSession(private val disconnectAll: () -> Unit) {
    private val mutableState = MutableStateFlow(RideState())
    val state = mutableState.asStateFlow()
    val keepsConnections get() = state.value.phase in setOf(RidePhase.STARTING, RidePhase.ACTIVE)

    fun begin(): Boolean {
        if (state.value.phase !in setOf(RidePhase.IDLE, RidePhase.ERROR)) return false
        mutableState.value = RideState(RidePhase.STARTING)
        return true
    }

    fun activate(nowMs: Long) {
        if (state.value.phase == RidePhase.STARTING) mutableState.value = RideState(RidePhase.ACTIVE, nowMs)
    }

    fun uiHidden() { if (!keepsConnections) disconnectAll() }

    fun end() {
        mutableState.value = state.value.copy(phase = RidePhase.STOPPING)
        disconnectAll()
        mutableState.value = RideState()
    }

    fun fail(error: RideError) {
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
