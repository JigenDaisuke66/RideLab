package com.isaac.ridelab.data.model

/** Latest recording snapshot, independent of notification frequency and database writes. */
data class LiveRideData(
    val trainerPowerWatts: Int? = null,
    val cadenceRpm: Double? = null,
    val heartRateBpm: Int? = null,
    // Last requested trainer target, NOT proof that the trainer applied that target.
    val targetPowerWatts: Int? = null,
    val externalPowerWatts: Int? = null,
    val speedKph: Double? = null,
    val resistanceLevel: Double? = null,
    val trainerAddress: String? = null,
    val heartRateSensorAddress: String? = null,
) {
    fun sample(sessionId: Long, epochMillis: Long, elapsedMillis: Long) = RideSample(sessionId,
        epochMillis, elapsedMillis, trainerPowerWatts, cadenceRpm, heartRateBpm, targetPowerWatts,
        externalPowerWatts, speedKph, resistanceLevel, trainerAddress, heartRateSensorAddress)

    companion object {
        fun snapshot(trainer: GattDeviceState, hr: HeartRateState, nowMs: Long): LiveRideData {
            fun <T> fresh(metric: TimedMetric<T>?) = metric?.takeUnless { it.isStale(nowMs) }?.value
            val cps = trainer.cyclingPower
            val power = if (!trainer.isActive) null else if (trainer.measurementSource == MeasurementSource.CYCLING_POWER)
                fresh(cps?.let { TimedMetric(it.measurement.instantaneousPowerWatts, it.receivedAtMs) })
                else fresh(trainer.metrics.trainerPowerWatts)
            val cadence = if (!trainer.isActive) null else if (trainer.measurementSource == MeasurementSource.CYCLING_POWER)
                fresh(cps?.cadenceRpm) else fresh(trainer.metrics.cadenceRpm)
            return LiveRideData(power, cadence,
                if (hr.isActive && hr.receivedAtMs?.let { nowMs - it < 5_000 } == true) hr.heartRateBpm else null,
                if (trainer.isActive) trainer.lastRequestedTarget else null,
                speedKph = if (trainer.isActive && trainer.measurementSource == MeasurementSource.FTMS) fresh(trainer.metrics.speedKph) else null,
                trainerAddress = trainer.device?.address, heartRateSensorAddress = hr.device?.address)
        }
    }
}

