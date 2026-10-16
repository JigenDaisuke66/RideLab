package com.isaac.ridelab.ble.control

import com.isaac.ridelab.ble.fec.*
import com.isaac.ridelab.ble.ftms.FtmsControlProtocol
import com.isaac.ridelab.ble.ftms.FtmsControlState
import com.isaac.ridelab.data.model.GattServiceInfo

enum class ControlProtocol { NONE, FEC, FTMS }
enum class ControlStatus { UNAVAILABLE, PREPARING, READY, REQUESTING, WRITING, TRANSPORT_SENT, ACCEPTED, FAILED, DISCONNECTED }
enum class ControlIssue { UNSUPPORTED, RANGE_UNAVAILABLE, INVALID_TARGET, REQUEST_REJECTED, TARGET_REJECTED, TRANSPORT, TIMEOUT, PERMISSION_LOST, MALFORMED_RESPONSE }
enum class ControlOwner { MANUAL, WORKOUT }
enum class LimitSource { VERIFIED_PROFILE, DEVICE_REPORTED, CONSERVATIVE }

data class TrainerCapabilities(
    val protocol: ControlProtocol = ControlProtocol.NONE,
    val powerRange: TrainerErgProfile? = null,
    val limitSource: LimitSource? = null,
    val automaticErg: Boolean = false,
    val issue: ControlIssue? = null,
) {
    val supportsTargetPower get() = protocol != ControlProtocol.NONE && powerRange != null
}

data class TrainerControlState(
    val capabilities: TrainerCapabilities = TrainerCapabilities(),
    val status: ControlStatus = ControlStatus.UNAVAILABLE,
    val authorized: Boolean = false,
    val requestedWatts: Int? = null,
    val acceptedWatts: Int? = null,
    val issue: ControlIssue? = null,
    val resultCode: Int? = null,
) {
    val canSend get() = capabilities.supportsTargetPower && status in setOf(
        ControlStatus.READY, ControlStatus.TRANSPORT_SENT, ControlStatus.ACCEPTED)
    val canWorkout get() = canSend && capabilities.automaticErg
}

/** Both manual and workout requests must pass this gate before the single selected adapter. */
object TrainerControlPolicy {
    /** Exactly one adapter receives an authorized request. No fallback/broadcast/queued replay. */
    fun dispatch(state: TrainerControlState, watts: Int, owner: ControlOwner, workoutOwnsControl: Boolean,
        fec: (Int) -> Boolean, ftms: (Int) -> Boolean): Boolean {
        if (!permits(state, watts, owner, workoutOwnsControl)) return false
        return when (state.capabilities.protocol) {
            ControlProtocol.FEC -> fec(watts)
            ControlProtocol.FTMS -> ftms(watts)
            ControlProtocol.NONE -> false
        }
    }

    fun preserveProtocol(candidate: TrainerCapabilities, required: ControlProtocol?): TrainerCapabilities =
        if (required != null && required != ControlProtocol.NONE && required != candidate.protocol)
            TrainerCapabilities(issue = ControlIssue.UNSUPPORTED) else candidate

    fun permits(state: TrainerControlState, watts: Int, owner: ControlOwner, workoutOwnsControl: Boolean): Boolean =
        state.canSend && state.capabilities.powerRange?.accepts(watts) == true && when (owner) {
            ControlOwner.MANUAL -> !workoutOwnsControl
            ControlOwner.WORKOUT -> workoutOwnsControl && state.capabilities.automaticErg
        }

    // Name only chooses the known operating profile AFTER the actual FEC layout is confirmed.
    // No other device gets the X2 MAX limit or permission for automatic workouts.
    fun verifiedX2(name: String?): Boolean = name?.let {
        it.startsWith("Think X211-", ignoreCase = true) ||
            it.equals("ThinkRider X2 MAX", ignoreCase = true) || it.equals("ThinkRider X2Max", ignoreCase = true)
    } == true

    fun select(name: String?, services: List<GattServiceInfo>, features: ByteArray?, range: ByteArray?): TrainerCapabilities {
        if (FecProtocol.detect(services).supported) {
            val verified = verifiedX2(name)
            return TrainerCapabilities(ControlProtocol.FEC,
                if (verified) ThinkRiderX2MaxProfile else TrainerErgProfile(50, 200),
                if (verified) LimitSource.VERIFIED_PROFILE else LimitSource.CONSERVATIVE, verified)
        }
        if (!FtmsControlProtocol.supportsErg(services, features)) return TrainerCapabilities(issue = ControlIssue.UNSUPPORTED)
        val limits = FtmsControlProtocol.powerRange(range)
            ?: return TrainerCapabilities(issue = ControlIssue.RANGE_UNAVAILABLE)
        return TrainerCapabilities(ControlProtocol.FTMS, limits, LimitSource.DEVICE_REPORTED)
    }

    fun project(capabilities: TrainerCapabilities, fec: FecState, ftms: FtmsControlState): TrainerControlState =
        when (capabilities.protocol) {
            ControlProtocol.NONE -> TrainerControlState(capabilities, issue = capabilities.issue)
            ControlProtocol.FTMS -> TrainerControlState(capabilities, ftms.status, ftms.authorized,
                ftms.requestedWatts, ftms.acceptedWatts, ftms.issue, ftms.resultCode)
            ControlProtocol.FEC -> TrainerControlState(capabilities, when (fec.status) {
                FecStatus.UNAVAILABLE -> ControlStatus.UNAVAILABLE
                FecStatus.WAITING, FecStatus.SUBSCRIBING -> ControlStatus.PREPARING
                FecStatus.READY -> ControlStatus.READY
                FecStatus.WRITING -> ControlStatus.WRITING
                FecStatus.SENT -> ControlStatus.TRANSPORT_SENT
                FecStatus.FAILED -> ControlStatus.FAILED
                FecStatus.DISCONNECTED -> ControlStatus.DISCONNECTED
            }, requestedWatts = fec.lastTargetWatts, resultCode = fec.statusCode,
                issue = if (fec.status == FecStatus.FAILED) ControlIssue.TRANSPORT else null)
        }
}
