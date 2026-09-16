package com.isaac.ridelab.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "ride_samples", primaryKeys = ["sessionId", "elapsedMillis"],
    foreignKeys = [ForeignKey(entity = RideSession::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class RideSample(
    val sessionId: Long,
    val timestampEpochMillis: Long,
    val elapsedMillis: Long,
    val trainerPowerWatts: Int?,
    val cadenceRpm: Double?,
    val heartRateBpm: Int?,
    val targetPowerWatts: Int?,
    val externalPowerWatts: Int? = null,
    val speedKph: Double? = null,
    val resistanceLevel: Double? = null,
    val trainerAddress: String? = null,
    val heartRateSensorAddress: String? = null,
)
