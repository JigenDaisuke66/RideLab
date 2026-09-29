package com.isaac.ridelab.data.repository

import com.isaac.ridelab.data.local.RideDao
import com.isaac.ridelab.data.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RideFtpSnapshotTest {
    private class MemoryDao : RideDao() {
        val sessions = mutableMapOf<Long, RideSession>()
        override suspend fun insertSession(session: RideSession): Long {
            val id = sessions.size + 1L
            sessions[id] = session.copy(id = id)
            return id
        }
        override suspend fun previousCompleted(timestamp: Long, id: Long) = sessions.values.filter { it.status == StoredRideStatus.COMPLETED && it.id < id }
        override suspend fun deleteHistory(id: Long): Int {
            if (sessions[id]?.status !in listOf(StoredRideStatus.COMPLETED, StoredRideStatus.ABORTED)) return 0
            sessions.remove(id)
            return 1
        }
        override suspend fun session(id: Long) = sessions[id]
        override suspend fun sessionCount() = sessions.size
        override suspend fun insertSample(sample: RideSample) = Unit
        override suspend fun samples(id: Long) = emptyList<RideSample>()
        override fun observeHistory() = flowOf(emptyList<RideHistoryEntry>())
        override suspend fun checkpoint(id: Long, duration: Long, target: Int?) = Unit
        override suspend fun finalizeSession(id: Long, end: Long, duration: Long, status: StoredRideStatus, target: Int?): Int = 0
        override suspend fun recoverInterrupted() = 0
    }

    @Test fun weightSnapshotsAtStartAndNeverChangesOnDetailRead() = runBlocking {
        val dao = MemoryDao(); var weight: Double? = 100.5
        val repo = RoomRideRepository(dao, { 130 }, { weight })
        val old = repo.start(RideSession(startTimeEpochMillis = 0))
        weight = 90.25
        val newer = repo.start(RideSession(startTimeEpochMillis = 1000))
        assertEquals(100.5, repo.analyticsSession(old)!!.bodyWeightKg!!, 0.0)
        assertEquals(90.25, repo.analyticsSession(newer)!!.bodyWeightKg!!, 0.0)
        weight = null
        val unset = repo.start(RideSession(startTimeEpochMillis = 2000))
        assertNull(repo.analyticsSession(unset)!!.bodyWeightKg)
        assertEquals(100.5, repo.analyticsSession(old)!!.bodyWeightKg!!, 0.0)
    }
    @Test fun startSnapshots130() = runBlocking {
        val dao = MemoryDao(); val repo = RoomRideRepository(dao) { 130 }
        val id = repo.start(RideSession(startTimeEpochMillis = 0))
        assertEquals(130, dao.sessions.getValue(id).analyticsFtpWatts)
    }
    @Test fun changingGlobalFtpDoesNotChangeOldSession() = runBlocking {
        val dao = MemoryDao(); var ftp: Int? = 130
        val repo = RoomRideRepository(dao) { ftp }
        val id = repo.start(RideSession(startTimeEpochMillis = 0))
        ftp = 170
        assertEquals(130, repo.analyticsSession(id)!!.analyticsFtpWatts)
    }
    @Test fun newRideUsesNewFtp() = runBlocking {
        val dao = MemoryDao(); var ftp: Int? = 130
        val repo = RoomRideRepository(dao) { ftp }
        repo.start(RideSession(startTimeEpochMillis = 0))
        ftp = 170
        val id = repo.start(RideSession(startTimeEpochMillis = 1000))
        assertEquals(170, repo.analyticsSession(id)!!.analyticsFtpWatts)
    }
    @Test fun unsetAtStartRemainsNullAfterSettingFtpAndOpeningDetails() = runBlocking {
        val dao = MemoryDao(); var ftp: Int? = null
        val repo = RoomRideRepository(dao) { ftp }
        val id = repo.start(RideSession(startTimeEpochMillis = 0))
        assertNull(dao.sessions.getValue(id).analyticsFtpWatts)
        ftp = 170
        assertNull(repo.analyticsSession(id)!!.analyticsFtpWatts)
    }
    @Test fun detailReadsNeverReadGlobalFtpOrWriteSessionIncludingLegacyNull() = runBlocking {
        val dao = MemoryDao()
        val old = dao.insertSession(RideSession(startTimeEpochMillis = 0, status = StoredRideStatus.COMPLETED, analyticsFtpWatts = 130))
        val legacy = dao.insertSession(RideSession(startTimeEpochMillis = 1000, status = StoredRideStatus.COMPLETED))
        val before = dao.sessions.toMap()
        val repo = RoomRideRepository(dao) { error("Details must not read global FTP") }
        repeat(2) { assertEquals(130, repo.analyticsSession(old)!!.analyticsFtpWatts); assertNull(repo.analyticsSession(legacy)!!.analyticsFtpWatts) }
        assertEquals(before, dao.sessions)
    }
}
