package com.isaac.ridelab.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StoredRideStatus { ACTIVE, COMPLETED, ABORTED }

@Entity(tableName = "ride_sessions", indices = [Index(value = ["activeSlot"], unique = true)])
data class RideSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long? = null,
    val durationMillis: Long = 0,
    val status: StoredRideStatus = StoredRideStatus.ACTIVE,
    val trainerName: String? = null,
    val trainerAddress: String? = null,
    val heartRateSensorName: String? = null,
    val heartRateSensorAddress: String? = null,
    val lastTargetPowerWatts: Int? = null,
    // SQLite permits multiple NULLs in a unique index, but only one ACTIVE slot.
    val activeSlot: Int? = 1,
    val analyticsFtpWatts: Int? = null,
)

