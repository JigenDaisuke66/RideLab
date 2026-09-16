package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.ble.power.CyclingPowerMeasurement
import com.isaac.ridelab.ble.fec.FecState
import org.junit.Assert.*
import org.junit.Test

class LiveRideDataTest {
    @Test fun freshTrainerAndHrAreSampledSeparately() {
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING, measurementSource = MeasurementSource.CYCLING_POWER,
            cyclingPower = CyclingPowerLiveData(CyclingPowerMeasurement(0, 102, null, null), 1000, TimedMetric(81.0, 1000)),
            fec = FecState(lastTargetWatts = 100))
        val hr = HeartRateState(connection = ConnectionState.STREAMING, heartRateBpm = 134, receivedAtMs = 1000)
        val data = LiveRideData.snapshot(trainer, hr, 2000)
        assertEquals(102, data.trainerPowerWatts); assertEquals(81.0, data.cadenceRpm)
        assertEquals(134, data.heartRateBpm); assertEquals(100, data.targetPowerWatts)
        assertNull(data.externalPowerWatts); assertNull(data.speedKph); assertNull(data.resistanceLevel)
    }

    @Test fun missingCadenceAndStaleMeasurementsRemainNull() {
        val trainer = GattDeviceState(connection = ConnectionState.STREAMING, measurementSource = MeasurementSource.CYCLING_POWER,
            cyclingPower = CyclingPowerLiveData(CyclingPowerMeasurement(0, 100, null, null), 1000, null))
        assertNull(LiveRideData.snapshot(trainer, HeartRateState(), 1000).cadenceRpm)
        val data = LiveRideData.snapshot(trainer, HeartRateState(connection = ConnectionState.STREAMING,
            heartRateBpm = 140, receivedAtMs = 1000), 6000)
        assertNull(data.trainerPowerWatts); assertNull(data.heartRateBpm)
    }

    @Test fun disconnectedSourcesCannotPublishHeldValues() {
        val hr = HeartRateState(heartRateBpm = 140, receivedAtMs = 1000)
        val trainer = GattDeviceState(metrics = TrainerMetrics(trainerPowerWatts = TimedMetric(100, 1000)))
        val data = LiveRideData.snapshot(trainer, hr, 1000)
        assertNull(data.trainerPowerWatts); assertNull(data.heartRateBpm)
    }
}
