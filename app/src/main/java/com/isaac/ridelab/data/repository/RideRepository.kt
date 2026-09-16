package com.isaac.ridelab.data.repository

import com.isaac.ridelab.data.local.RideDao
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession
import com.isaac.ridelab.data.model.StoredRideStatus

interface RideRepository {
    suspend fun recoverInterrupted(): Int
    suspend fun start(session: RideSession): Long
    suspend fun append(sample: RideSample)
    suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?)
}

class RoomRideRepository(private val dao: RideDao, private val currentFtp: suspend () -> Int?) : RideRepository {
    suspend fun analyticsSession(id: Long) = dao.session(id)
    suspend fun historySamples(id: Long) = dao.samples(id)
    fun observeHistory() = dao.observeHistory()
    override suspend fun recoverInterrupted() = dao.recoverInterrupted()
    override suspend fun start(session: RideSession) = dao.insertSession(session.copy(analyticsFtpWatts = currentFtp()))
    override suspend fun append(sample: RideSample) = dao.append(sample)
    override suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?) =
        dao.finish(id, finalSample, end, duration, status, target)
}
