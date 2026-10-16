package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.ble.fec.TrainerErgProfile
import org.junit.Assert.*
import org.junit.Test

class FtmsControlSessionTest {
    /** Fake ATT behavior using the same serialized queue and protocol state machine as production. */
    private class FakeGatt {
        val packets = mutableListOf<ByteArray>()
        var deadline = false
        var dispatchOk = true
        val queue = SerialGattOperations({}, {}, { session.failure(ControlIssue.TIMEOUT) })
        val session: FtmsControlSession = FtmsControlSession({ packet ->
            check(queue.idle)
            queue.enqueue("cp", { packets.add(packet.copyOf()); dispatchOk }) { status, _ -> session.onWrite(status) }
            dispatchOk
        }, { deadline = true }, { deadline = false }, {})
        init { session.configure(TrainerErgProfile(50, 1500)) }
        fun att(status: Int = 0) { queue.complete("cp", status) }
        fun response(op: Int, result: Int = 1) { session.onIndication(byteArrayOf(0x80.toByte(), op.toByte(), result.toByte())) }
        fun acceptControl() { att(); response(0) }
        fun acceptTarget() { att(); response(5) }
    }
    @Test fun explicitRequestIsRequiredAndTransportSuccessIsNotAcceptance() {
        val f = FakeGatt()
        assertTrue(f.packets.isEmpty())
        assertFalse(f.session.state.authorized)
        assertTrue(f.session.setTargetPower(100))
        assertArrayEquals(byteArrayOf(0), f.packets.single())
        f.att()
        assertEquals(ControlStatus.REQUESTING, f.session.state.status)
        assertFalse(f.session.state.authorized)
        f.response(0)
        assertArrayEquals(byteArrayOf(5, 100, 0), f.packets.last())
        f.att()
        assertNull(f.session.state.acceptedWatts)
        f.response(5)
        assertEquals(ControlStatus.ACCEPTED, f.session.state.status)
        assertEquals(100, f.session.state.acceptedWatts)
        assertFalse(f.deadline)
    }
    @Test fun signed16EncodingIsLittleEndianAndSeparateFromOperatingLimits() {
        assertArrayEquals(byteArrayOf(5, 0xd0.toByte(), 7), FtmsControlProtocol.targetPower(2000))
        assertArrayEquals(byteArrayOf(5, -1, 0x7f), FtmsControlProtocol.targetPower(32767))
        for (invalid in listOf(-1, 32768, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { FtmsControlProtocol.targetPower(invalid) }
        }
    }
    @Test fun rejectedControlNeverSendsPendingTarget() {
        for (code in 2..5) {
            val f = FakeGatt(); f.session.setTargetPower(100); f.att(); f.response(0, code)
            assertEquals(1, f.packets.size)
            assertFalse(f.session.state.authorized)
            assertEquals(ControlIssue.REQUEST_REJECTED, f.session.state.issue)
            assertEquals(code, f.session.state.resultCode)
            assertFalse(f.session.setTargetPower(150))
        }
    }
    @Test fun targetRejectionNeverReplacesLastAcceptedTarget() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.acceptControl(); f.acceptTarget()
        f.session.setTargetPower(150); f.att(); f.response(5, 3)
        assertEquals(100, f.session.state.acceptedWatts)
        assertEquals(ControlIssue.TARGET_REJECTED, f.session.state.issue)
        assertEquals(3, f.packets.size)
    }
    @Test fun targetResponseControlNotPermittedRevokesAuthorization() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.acceptControl(); f.att(); f.response(5, 5)
        assertFalse(f.session.state.authorized)
        assertEquals(ControlIssue.PERMISSION_LOST, f.session.state.issue)
        assertNull(f.session.state.acceptedWatts)
    }
    @Test fun indicationBeforeWriteCompletionCannotOverlapAttOperations() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.response(0)
        assertEquals(1, f.packets.size); assertFalse(f.session.state.authorized)
        f.att(); assertEquals(2, f.packets.size)
        f.response(5); assertNull(f.session.state.acceptedWatts)
        f.att(); assertEquals(100, f.session.state.acceptedWatts)
    }
    @Test fun duplicateClicksAndWrongOpcodeResponsesDoNotQueueTargets() {
        val f = FakeGatt(); assertTrue(f.session.setTargetPower(100))
        assertFalse(f.session.setTargetPower(150))
        f.response(5); f.att(); assertEquals(1, f.packets.size)
        f.response(0); f.response(0)
        assertFalse(f.session.setTargetPower(150)); assertEquals(2, f.packets.size)
        f.acceptTarget(); f.response(5); assertEquals(2, f.packets.size)
        assertEquals(100, f.session.state.acceptedWatts)
    }
    @Test fun timeoutQuarantinesControlAndIgnoresLateResponse() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.att(); f.session.timeout()
        f.response(0)
        assertEquals(ControlIssue.TIMEOUT, f.session.state.issue)
        assertFalse(f.session.setTargetPower(100)); assertEquals(1, f.packets.size)
    }
    @Test fun disconnectDropsPendingAndReconnectDoesNotReplayOrRetainAuthorization() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.acceptControl()
        f.session.reset(); f.att(); f.response(5)
        assertEquals(ControlStatus.DISCONNECTED, f.session.state.status)
        assertNull(f.session.state.requestedWatts); assertNull(f.session.state.acceptedWatts)
        f.session.configure(TrainerErgProfile(50, 1500))
        assertFalse(f.session.state.authorized); assertEquals(2, f.packets.size)
        f.session.setTargetPower(125)
        assertArrayEquals(byteArrayOf(0), f.packets.last())
    }
    @Test fun invalidTargetsAreNotClampedOrWritten() {
        val f = FakeGatt()
        for (value in listOf(-1, 49, 1501, Int.MAX_VALUE)) assertFalse(f.session.setTargetPower(value))
        assertTrue(f.packets.isEmpty()); assertEquals(ControlIssue.INVALID_TARGET, f.session.state.issue)
        assertTrue(f.session.setTargetPower(50))
    }
    @Test fun transportFailureAndDispatchFailureStopProcedure() {
        val f = FakeGatt(); f.session.setTargetPower(100); f.att(133); f.response(0)
        assertEquals(ControlIssue.TRANSPORT, f.session.state.issue); assertEquals(1, f.packets.size)
        val rejected = FakeGatt(); rejected.dispatchOk = false
        assertFalse(rejected.session.setTargetPower(100))
        assertEquals(ControlStatus.FAILED, rejected.session.state.status)
    }
    @Test fun authorizationLostOrResetCancelsPendingChain() {
        for (op in listOf(0xff, 1)) {
            val f = FakeGatt(); f.session.setTargetPower(100)
            f.session.onMachineStatus(byteArrayOf(op.toByte())); f.acceptControl()
            assertEquals(ControlIssue.PERMISSION_LOST, f.session.state.issue)
            assertEquals(1, f.packets.size)
        }
    }
    @Test fun malformedIndicationsFailClosedAndUnsolicitedResponsesCannotAuthorize() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(-128, 0), byteArrayOf(-128, 0, 0), byteArrayOf(-128, 0, 1, 0))) {
            val f = FakeGatt(); f.response(0); assertFalse(f.session.state.authorized)
            f.session.setTargetPower(100); f.session.onIndication(bytes)
            assertEquals(ControlIssue.MALFORMED_RESPONSE, f.session.state.issue)
            assertEquals(1, f.packets.size)
        }
    }
}
