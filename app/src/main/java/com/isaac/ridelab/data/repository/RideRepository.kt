package com.isaac.ridelab.data.repository

import com.isaac.ridelab.data.local.RideDao
import com.isaac.ridelab.analytics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession
import com.isaac.ridelab.data.model.StoredRideStatus

interface RideRepository {
    suspend fun recoverInterrupted(): Int
    suspend fun start(session: RideSession): Long
    suspend fun append(sample: RideSample)
    suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?)
}

class RoomRideRepository(private val dao: RideDao, private val currentFtp: suspend () -> Int?, private val currentWeight: suspend () -> Double?) : RideRepository {
    constructor(dao: RideDao, currentFtp: suspend () -> Int?) : this(dao, currentFtp, { null })
    suspend fun powerAnalysis(session: RideSession, samples: List<RideSample>): PowerAnalysis = withContext(Dispatchers.Default) {
        val current = PowerAnalysisCalculator.calculate(samples, session)
        val previous = arrayOfNulls<Double>(PowerAnalysisCalculator.durations.size)
        if (session.status == StoredRideStatus.COMPLETED) {
            for (ride in dao.previousCompleted(session.startTimeEpochMillis, session.id)) {
                coroutineContext.ensureActive()
                if (!PowerAnalysisCalculator.eligiblePrevious(session, ride)) continue
                val result = PowerAnalysisCalculator.calculate(dao.samples(ride.id), ride)
                result.efforts.forEachIndexed { i, effort -> effort.watts?.let { if (previous[i] == null || it > previous[i]!!) previous[i] = it } }
            }
        }
        current.copy(prs = current.efforts.mapIndexed { i, effort -> PowerAnalysisCalculator.compare(effort.watts, previous[i], session.status) })
    }
    suspend fun exportSamples(id: Long) = dao.exportSamples(id)
    suspend fun backupHistory() = dao.backupHistory()
    suspend fun restoreHistory(rides: List<com.isaac.ridelab.export.BackupRide>) = dao.restoreHistory(rides)
    suspend fun deleteHistory(id: Long): Boolean = dao.deleteHistory(id) == 1
    suspend fun analyticsSession(id: Long) = dao.session(id)
    suspend fun historySamples(id: Long) = dao.samples(id)
    fun observeHistory() = dao.observeHistory()
    override suspend fun recoverInterrupted() = dao.recoverInterrupted()
    override suspend fun start(session: RideSession) = dao.insertSession(session.copy(analyticsFtpWatts = currentFtp(), bodyWeightKg = currentWeight()))
    override suspend fun append(sample: RideSample) = dao.append(sample)
    override suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?) =
        dao.finish(id, finalSample, end, duration, status, target)
}
