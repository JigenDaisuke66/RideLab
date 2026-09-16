package com.isaac.ridelab.data.model

import androidx.room.Embedded

/** SQL AVG/MAX ignore nulls; a genuine zero remains a measurement. */
data class RideHistoryEntry(
    @Embedded val session: RideSession,
    val sampleCount: Long,
    val firstElapsedMillis: Long?,
    val lastElapsedMillis: Long?,
    val averagePower: Double?,
    val maximumPower: Int?,
    val averageHeartRate: Double?,
    val maximumHeartRate: Int?,
    val averageCadence: Double?,
    val maximumCadence: Double?,
)
