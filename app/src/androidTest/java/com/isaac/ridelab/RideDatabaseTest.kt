package com.isaac.ridelab

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.local.RideDatabase
import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.RideSession
import com.isaac.ridelab.data.model.StoredRideStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Device-side Room integration checks; compile-only until the user runs Android Studio tests. */
class RideDatabaseTest {
    private lateinit var db: RideDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RideDatabase::class.java).build()
    }
    @After fun tearDown() { db.close() }

    @Test fun exportLoadsOnlySelectedFinishedRideInElapsedOrder() = runBlocking {
        val dao = db.rides()
        val id = dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        dao.insertSample(LiveRideData(trainerPowerWatts = 0).sample(id, 3000, 2000))
        dao.insertSample(LiveRideData().sample(id, 1000, 0))
        dao.finish(id, null, 3000, 2000, StoredRideStatus.COMPLETED, null)
        val active = dao.insertSession(RideSession(startTimeEpochMillis = 4000))
        dao.append(LiveRideData(trainerPowerWatts = 200).sample(active, 4000, 0))
        val samples = dao.exportSamples(id)
        assertEquals(listOf(0L, 2000L), samples.map { it.elapsedMillis })
        assertNull(samples.first().trainerPowerWatts)
        assertEquals(0, samples.last().trainerPowerWatts)
        try { dao.exportSamples(active); fail("Active export must be rejected") }
        catch (_: IllegalStateException) { }
        try { dao.exportSamples(-1); fail("Missing export must be rejected") }
        catch (_: IllegalStateException) { }
    }

    @Test fun deleteHistoryCascadesSamplesAndProtectsActiveRide() = runBlocking {
        val dao = db.rides()
        val completed = dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        dao.finish(completed, LiveRideData().sample(completed, 2000, 1000),
            2000, 1000, StoredRideStatus.COMPLETED, null)
        val aborted = dao.insertSession(RideSession(startTimeEpochMillis = 3000))
        dao.append(LiveRideData().sample(aborted, 4000, 1000))
        dao.recoverInterrupted()
        val active = dao.insertSession(RideSession(startTimeEpochMillis = 5000))
        dao.append(LiveRideData().sample(active, 6000, 1000))
        assertEquals(0, dao.deleteHistory(active))
        assertEquals(1, dao.samples(active).size)
        assertEquals(1, dao.deleteHistory(completed))
        assertNull(dao.session(completed))
        assertTrue(dao.samples(completed).isEmpty())
        assertEquals(listOf(aborted), dao.observeHistory().first().map { it.session.id })
        assertEquals(0, dao.deleteHistory(completed))
        assertEquals(1, dao.deleteHistory(aborted))
        assertTrue(dao.samples(aborted).isEmpty())
        assertNotNull(dao.session(active))
    }

    @Test fun historyProjectionMapsCountsAndNullableMetrics() = runBlocking {
        val dao = db.rides()
        val first = dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        dao.append(LiveRideData().sample(first, 1000, 0))
        dao.finish(first, LiveRideData(trainerPowerWatts = 100, heartRateBpm = 130, cadenceRpm = 80.0).sample(first, 2000, 1000),
            2000, 1000, StoredRideStatus.COMPLETED, null)
        val interrupted = dao.insertSession(RideSession(startTimeEpochMillis = 3000))
        dao.recoverInterrupted()
        dao.insertSession(RideSession(startTimeEpochMillis = 4000))
        val history = dao.observeHistory().first()
        assertEquals(listOf(interrupted, first), history.map { it.session.id })
        assertEquals(0L, history[0].sampleCount)
        assertNull(history[0].averagePower)
        assertNull(history[0].firstElapsedMillis)
        val entry = history[1]
        assertEquals(2L, entry.sampleCount)
        assertEquals(100.0, entry.averagePower!!, 0.0)
        assertEquals(100, entry.maximumPower)
        assertEquals(130.0, entry.averageHeartRate!!, 0.0)
        assertEquals(80.0, entry.averageCadence!!, 0.0)
        assertEquals(0L, entry.firstElapsedMillis)
        assertEquals(1000L, entry.lastElapsedMillis)
    }
    @Test fun sessionSampleAndCompletionRoundTrip() = runBlocking {
        val dao = db.rides()
        val id = dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        dao.append(LiveRideData().sample(id, 2000, 1000))
        dao.finish(id, LiveRideData(trainerPowerWatts = 102, targetPowerWatts = 100).sample(id, 3500, 2500),
            3500, 2500, StoredRideStatus.COMPLETED, 100)
        val stored = dao.session(id)!!
        assertEquals(StoredRideStatus.COMPLETED, stored.status)
        assertEquals(3500L, stored.endTimeEpochMillis); assertEquals(2500L, stored.durationMillis)
        assertNull(stored.activeSlot)
        assertEquals(1, dao.sessionCount()); assertEquals(2, dao.samples(id).size)
        assertNull(dao.samples(id).first().trainerPowerWatts)
        assertNull(dao.samples(id).first().heartRateBpm)
    }

    @Test fun recoveryUsesLastCheckpointAndMarksAborted() = runBlocking {
        val dao = db.rides()
        val id = dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        dao.append(LiveRideData().sample(id, 9000, 8000))
        assertEquals(1, dao.recoverInterrupted())
        assertEquals(0, dao.recoverInterrupted())
        val stored = dao.session(id)!!
        assertEquals(StoredRideStatus.ABORTED, stored.status)
        assertEquals(9000L, stored.endTimeEpochMillis); assertEquals(8000L, stored.durationMillis)
    }

    @Test fun databaseRejectsSecondActiveSessionAndInvalidForeignKey() = runBlocking {
        val dao = db.rides()
        dao.insertSession(RideSession(startTimeEpochMillis = 1000))
        try { dao.insertSession(RideSession(startTimeEpochMillis = 2000)); fail("Expected unique ACTIVE slot") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { dao.insertSample(LiveRideData().sample(999, 2000, 0)); fail("Expected foreign key constraint") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
    }
}
