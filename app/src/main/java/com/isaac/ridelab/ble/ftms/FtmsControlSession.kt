package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.ble.fec.TrainerErgProfile

data class FtmsControlState(
    val status: ControlStatus = ControlStatus.UNAVAILABLE,
    val authorized: Boolean = false,
    val requestedWatts: Int? = null,
    val acceptedWatts: Int? = null,
    val issue: ControlIssue? = null,
    val resultCode: Int? = null,
)

/** Pure single-transaction state machine. ATT write completion AND matching indication are
 * required before progressing. There is no retry/target queue and no reconnect replay.
 * A timeout/malformed response quarantines control until a new GATT session is configured.
 */
class FtmsControlSession(
    private val write: (ByteArray) -> Boolean,
    private val armTimeout: () -> Unit,
    private val cancelTimeout: () -> Unit,
    private val publish: (FtmsControlState) -> Unit,
) {
    var state = FtmsControlState(); private set
    private var range: TrainerErgProfile? = null
    private var opcode: Int? = null
    private var writeCompleted = false
    private var response: FtmsControlProtocol.Response? = null
    private fun update(value: FtmsControlState) { state = value; publish(value) }
    fun configure(limits: TrainerErgProfile) {
        reset(); range = limits; update(FtmsControlState(status = ControlStatus.READY))
    }
    fun setTargetPower(watts: Int): Boolean {
        if (state.status !in setOf(ControlStatus.READY, ControlStatus.ACCEPTED)) return false
        if (range?.accepts(watts) != true) {
            update(state.copy(issue = ControlIssue.INVALID_TARGET)); return false
        }
        update(state.copy(requestedWatts = watts, issue = null, resultCode = null))
        return begin(if (state.authorized) FtmsControlProtocol.SET_TARGET_POWER else FtmsControlProtocol.REQUEST_CONTROL)
    }
    private fun begin(op: Int): Boolean {
        opcode = op; writeCompleted = false; response = null
        update(state.copy(status = if (op == 0) ControlStatus.REQUESTING else ControlStatus.WRITING))
        armTimeout()
        val bytes = if (op == 0) byteArrayOf(0) else FtmsControlProtocol.targetPower(state.requestedWatts!!)
        if (!write(bytes)) { failure(ControlIssue.TRANSPORT); return false }
        return state.status != ControlStatus.FAILED
    }
    fun onWrite(status: Int) {
        if (opcode == null || writeCompleted) return
        if (status != 0) { failure(ControlIssue.TRANSPORT, status); return }
        writeCompleted = true; finishIfComplete()
    }
    fun onIndication(bytes: ByteArray) {
        val op = opcode ?: return // unsolicited/duplicate response cannot authorize or apply a target
        val parsed = FtmsControlProtocol.response(bytes)
        if (parsed == null) { failure(ControlIssue.MALFORMED_RESPONSE); return }
        if (parsed.request != op || response != null) return
        response = parsed; finishIfComplete()
    }
    private fun finishIfComplete() {
        val result = response ?: return
        if (!writeCompleted) return
        val op = opcode ?: return
        cancelTimeout(); opcode = null; response = null
        if (result.result != FtmsControlProtocol.SUCCESS) {
            failure(when {
                op == 0 -> ControlIssue.REQUEST_REJECTED
                result.result == 5 -> ControlIssue.PERMISSION_LOST
                else -> ControlIssue.TARGET_REJECTED
            }, result.result)
        } else if (op == FtmsControlProtocol.REQUEST_CONTROL) {
            update(state.copy(authorized = true))
            begin(FtmsControlProtocol.SET_TARGET_POWER)
        } else {
            update(state.copy(status = ControlStatus.ACCEPTED, acceptedWatts = state.requestedWatts,
                resultCode = result.result, issue = null))
        }
    }
    fun onMachineStatus(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (bytes[0].toInt() and 255 in setOf(0xff, 0x01)) failure(ControlIssue.PERMISSION_LOST)
    }
    fun timeout() { if (opcode != null) failure(ControlIssue.TIMEOUT) }
    fun failure(issue: ControlIssue, code: Int? = null) {
        cancelTimeout(); opcode = null; response = null
        update(state.copy(status = ControlStatus.FAILED, authorized = false, issue = issue, resultCode = code))
    }
    fun reset() {
        cancelTimeout(); opcode = null; response = null; range = null; writeCompleted = false
        update(FtmsControlState(status = ControlStatus.DISCONNECTED))
    }
}
