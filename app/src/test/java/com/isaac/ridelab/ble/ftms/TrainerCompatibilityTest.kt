package com.isaac.ridelab.ble.ftms

import com.isaac.ridelab.ble.BleUuids
import com.isaac.ridelab.ble.control.*
import com.isaac.ridelab.ble.fec.*
import com.isaac.ridelab.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrainerCompatibilityTest {
    private fun ch(uuid: UUID, props: Int, cccd: Boolean = false) = GattCharacteristicInfo(uuid, props,
        if (cccd) listOf(FecProtocol.CCCD) else emptyList())
    private val fec = GattServiceInfo(FecProtocol.SERVICE, 0, listOf(
        ch(FecProtocol.NOTIFY, 0x10, true), ch(FecProtocol.WRITE, 0x0c)))
    private val cps = GattServiceInfo(BleUuids.CYCLING_POWER, 0, emptyList())
    private val ftms = GattServiceInfo(BleUuids.FITNESS_MACHINE, 0, listOf(
        ch(FtmsControlProtocol.FEATURE, 0x02), ch(FtmsControlProtocol.POWER_RANGE, 0x02),
        ch(FtmsConstants.INDOOR_BIKE_DATA, 0x10, true),
        ch(FtmsControlProtocol.CONTROL_POINT, 0x28, true), ch(FtmsControlProtocol.STATUS, 0x10, true)))
    private val feature = byteArrayOf(0, 0x40, 0, 0, 8, 0, 0, 0)
    private val range = byteArrayOf(25, 0, 0xdc.toByte(), 5, 5, 0) // 25..1500, 5 W
    private fun select(services: List<GattServiceInfo>, name: String? = null, features: ByteArray? = feature,
        powerRange: ByteArray? = range) = TrainerControlPolicy.select(name, services, features, powerRange)

    @Test fun x2CpsFecRetainsVerifiedLimitsAndWorkoutPermission() {
        val result = select(listOf(cps, fec), "Think X211-0013186")
        assertEquals(ControlProtocol.FEC, result.protocol)
        assertEquals(ThinkRiderX2MaxProfile, result.powerRange)
        assertTrue(result.automaticErg)
    }
    @Test fun ftmsErgUsesDeviceReportedRangeAndDisallowsAutomaticWorkouts() {
        val result = select(listOf(ftms))
        assertEquals(ControlProtocol.FTMS, result.protocol)
        assertEquals(TrainerErgProfile(25, 1500, 5), result.powerRange)
        assertEquals(LimitSource.DEVICE_REPORTED, result.limitSource)
        assertFalse(result.automaticErg)
    }
    @Test fun ftmsMeasurementOnlyDoesNotEnableControl() {
        assertEquals(ControlProtocol.NONE, select(listOf(ftms), features = ByteArray(8)).protocol)
        assertEquals(ControlProtocol.NONE, select(listOf(ftms), features = byteArrayOf(8)).protocol)
        for (uuid in listOf(FtmsControlProtocol.CONTROL_POINT, FtmsControlProtocol.FEATURE, FtmsControlProtocol.STATUS)) {
            val incomplete = ftms.copy(characteristics = ftms.characteristics.filterNot { it.uuid == uuid })
            assertEquals(ControlProtocol.NONE, select(listOf(incomplete)).protocol)
        }
    }
    @Test fun cpsAndDeviceNameAloneNeverEnableErg() {
        assertEquals(ControlProtocol.NONE, select(listOf(cps), "ThinkRider X2 MAX").protocol)
    }
    @Test fun bothProtocolsSelectFecOnceRegardlessOfServiceOrder() {
        assertEquals(ControlProtocol.FEC, select(listOf(fec, ftms), "Think X211-0013186").protocol)
        assertEquals(ControlProtocol.FEC, select(listOf(ftms, fec), "Think X211-0013186").protocol)
        val unknown = select(listOf(ftms, fec), "Another trainer")
        assertEquals(TrainerErgProfile(50, 200), unknown.powerRange)
        assertFalse(unknown.automaticErg)
    }
    @Test fun reconnectCannotSilentlySwitchTheActiveProtocol() {
        val candidate = select(listOf(ftms))
        assertEquals(ControlProtocol.NONE, TrainerControlPolicy.preserveProtocol(candidate, ControlProtocol.FEC).protocol)
        assertEquals(candidate, TrainerControlPolicy.preserveProtocol(candidate, ControlProtocol.FTMS))
        assertEquals(candidate, TrainerControlPolicy.preserveProtocol(candidate, null))
    }
    @Test fun dispatchCallsOnlySelectedTransportAndNeverFallsBackOnFailure() {
        val fecCalls = mutableListOf<Int>(); val ftmsCalls = mutableListOf<Int>()
        fun send(cap: TrainerCapabilities, watts: Int = 100, owner: ControlOwner = ControlOwner.MANUAL,
            workout: Boolean = false) = TrainerControlPolicy.dispatch(
            TrainerControlState(cap, ControlStatus.READY), watts, owner, workout,
            { fecCalls.add(it); false }, { ftmsCalls.add(it); true })
        assertFalse(send(select(listOf(ftms, fec), "Think X211-0013186")))
        assertEquals(listOf(100), fecCalls); assertTrue(ftmsCalls.isEmpty())
        assertTrue(send(select(listOf(ftms))))
        assertEquals(listOf(100), ftmsCalls)
        assertFalse(send(select(listOf(cps))))
        assertFalse(send(select(listOf(ftms)), 2000))
        assertFalse(send(select(listOf(ftms)), owner = ControlOwner.WORKOUT, workout = true))
        assertEquals(1, fecCalls.size); assertEquals(1, ftmsCalls.size)
    }
    @Test fun missingAndMalformedRangeFailClosed() {
        for (bytes in listOf(null, byteArrayOf(50), byteArrayOf(50, 0, 100, 0, 0, 0), byteArrayOf(100, 0, 50, 0, 1, 0))) {
            val result = select(listOf(ftms), powerRange = bytes)
            assertFalse(result.supportsTargetPower)
            assertEquals(ControlIssue.RANGE_UNAVAILABLE, result.issue)
        }
    }
    @Test fun controlRequiresWriteIndicateAndCccdNotWriteWithoutResponse() {
        for (props in listOf(0x04, 0x08, 0x20, 0x18)) {
            val broken = ftms.copy(characteristics = ftms.characteristics.map {
                if (it.uuid == FtmsControlProtocol.CONTROL_POINT) it.copy(properties = props) else it })
            assertFalse(select(listOf(broken)).supportsTargetPower)
        }
        assertFalse(select(listOf(ftms.copy(characteristics = ftms.characteristics.map {
            if (it.uuid == FtmsControlProtocol.CONTROL_POINT) it.copy(descriptorUuids = emptyList()) else it
        }))).supportsTargetPower)
    }
    @Test fun rangesRetainIncrementAlignmentAndRejectInvalidInputs() {
        val profile = FtmsControlProtocol.powerRange(range)!!
        assertTrue(profile.accepts(100)); assertFalse(profile.accepts(101))
        assertFalse(profile.accepts(24)); assertFalse(profile.accepts(1501))
        assertEquals(1500, profile.pending(9999))
        assertEquals(TrainerErgProfile(2, 100, 4), FtmsControlProtocol.powerRange(byteArrayOf(-10, -1, 100, 0, 4, 0)))
        assertNull(FtmsControlProtocol.powerRange(byteArrayOf(-10, -1, -1, -1, 1, 0)))
    }
    @Test fun manualAndWorkoutOwnershipUseSameGate() {
        val cap = select(listOf(cps, fec), "Think X211-0013186")
        val ready = TrainerControlState(cap, ControlStatus.READY)
        assertTrue(TrainerControlPolicy.permits(ready, 100, ControlOwner.MANUAL, false))
        assertFalse(TrainerControlPolicy.permits(ready, 100, ControlOwner.MANUAL, true))
        assertTrue(TrainerControlPolicy.permits(ready, 100, ControlOwner.WORKOUT, true))
        assertFalse(TrainerControlPolicy.permits(ready, 100, ControlOwner.WORKOUT, false))
        assertFalse(TrainerControlPolicy.permits(ready, 2001, ControlOwner.WORKOUT, true))
        assertFalse(TrainerControlPolicy.permits(ready.copy(status = ControlStatus.WRITING), 100, ControlOwner.MANUAL, false))
        val ftmsReady = ready.copy(capabilities = select(listOf(ftms)))
        assertFalse(TrainerControlPolicy.permits(ftmsReady, 100, ControlOwner.WORKOUT, true))
        assertTrue(TrainerControlPolicy.permits(ftmsReady, 100, ControlOwner.MANUAL, false))
    }
    @Test fun fecTransportSuccessIsNotProtocolAcceptance() {
        val cap = select(listOf(fec), "Think X211-0013186")
        val result = TrainerControlPolicy.project(cap, FecState(FecProtocol.detect(listOf(fec)), FecStatus.SENT, 2000), FtmsControlState())
        assertEquals(ControlStatus.TRANSPORT_SENT, result.status)
        assertNull(result.acceptedWatts)
        assertEquals(2000, result.requestedWatts)
    }
    @Test fun measurementSourceIsIndependentOfControlAndValuesAreNotCombined() {
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING, capabilities = select(listOf(ftms)),
            measurementSource = MeasurementSource.CYCLING_POWER,
            metrics = TrainerMetrics(trainerPowerWatts = TimedMetric(999, 100)),
            cyclingPower = CyclingPowerLiveData(com.isaac.ridelab.ble.power.CyclingPowerParser.parse(
                byteArrayOf(0, 0, 100, 0)).let { (it as com.isaac.ridelab.ble.power.CyclingPowerParseResult.Success).measurement }, 100, null))
        assertEquals(100, LiveRideData.snapshot(trainer, HeartRateState(), 100).trainerPowerWatts)
        assertNull(LiveRideData.snapshot(trainer, HeartRateState(), 100).externalPowerWatts)
    }
}
