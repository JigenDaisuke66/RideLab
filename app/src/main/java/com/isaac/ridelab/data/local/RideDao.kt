package com.isaac.ridelab.data.local

import androidx.room.*
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession
import com.isaac.ridelab.data.model.StoredRideStatus

@Dao
abstract class RideDao {
    @Query("SELECT * FROM ride_sessions WHERE status = 'COMPLETED' AND (startTimeEpochMillis < :timestamp OR (startTimeEpochMillis = :timestamp AND id < :id)) ORDER BY startTimeEpochMillis, id")
    abstract suspend fun previousCompleted(timestamp: Long, id: Long): List<RideSession>
    @Query("""SELECT r.*, COUNT(s.sessionId) AS sampleCount,
        MIN(s.elapsedMillis) AS firstElapsedMillis, MAX(s.elapsedMillis) AS lastElapsedMillis,
        AVG(s.trainerPowerWatts) AS averagePower, MAX(s.trainerPowerWatts) AS maximumPower,
        AVG(s.heartRateBpm) AS averageHeartRate, MAX(s.heartRateBpm) AS maximumHeartRate,
        AVG(s.cadenceRpm) AS averageCadence, MAX(s.cadenceRpm) AS maximumCadence
        FROM ride_sessions r LEFT JOIN ride_samples s ON s.sessionId = r.id
        WHERE r.status IN ('COMPLETED', 'ABORTED')
        GROUP BY r.id ORDER BY r.startTimeEpochMillis DESC, r.id DESC""")
    abstract fun observeHistory(): kotlinx.coroutines.flow.Flow<List<com.isaac.ridelab.data.model.RideHistoryEntry>>

    @Insert abstract suspend fun insertSession(session: RideSession): Long
    @Insert abstract suspend fun insertSample(sample: RideSample)
    @Query("SELECT * FROM ride_sessions WHERE id = :id") abstract suspend fun session(id: Long): RideSession?
    @Query("SELECT * FROM ride_samples WHERE sessionId = :id ORDER BY elapsedMillis") abstract suspend fun samples(id: Long): List<RideSample>
    @Query("SELECT COUNT(*) FROM ride_sessions") abstract suspend fun sessionCount(): Int

    @Query("UPDATE ride_sessions SET durationMillis = :duration, lastTargetPowerWatts = COALESCE(:target, lastTargetPowerWatts) WHERE id = :id AND status = 'ACTIVE'")
    abstract suspend fun checkpoint(id: Long, duration: Long, target: Int?)

    @Query("UPDATE ride_sessions SET endTimeEpochMillis = :end, durationMillis = :duration, status = :status, activeSlot = NULL, lastTargetPowerWatts = COALESCE(:target, lastTargetPowerWatts) WHERE id = :id AND status = 'ACTIVE'")
    abstract suspend fun finalizeSession(id: Long, end: Long, duration: Long, status: StoredRideStatus, target: Int?): Int

    @Query("""UPDATE ride_sessions SET status = 'ABORTED', activeSlot = NULL,
        endTimeEpochMillis = COALESCE((SELECT timestampEpochMillis FROM ride_samples WHERE sessionId = ride_sessions.id ORDER BY elapsedMillis DESC LIMIT 1), startTimeEpochMillis)
        WHERE status = 'ACTIVE'""")
    abstract suspend fun recoverInterrupted(): Int

    @Transaction
    open suspend fun append(sample: RideSample) {
        check(session(sample.sessionId)?.status == StoredRideStatus.ACTIVE)
        insertSample(sample)
        checkpoint(sample.sessionId, sample.elapsedMillis, sample.targetPowerWatts)
    }

    @Transaction
    open suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?) {
        if (finalSample != null) append(finalSample)
        check(finalizeSession(id, end, duration, status, target) == 1)
    }
}
