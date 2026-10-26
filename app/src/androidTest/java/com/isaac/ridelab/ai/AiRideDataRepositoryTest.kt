package com.isaac.ridelab.ai

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.local.RideDatabase
import com.isaac.ridelab.data.model.*
import com.isaac.ridelab.data.repository.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Isolated real Room/WAL file; compile checked only, pending execution on Android. */
class AiRideDataRepositoryTest {
    @Test fun rawSelectJoinsAndProfileCteReadSameDatabaseWithoutChangingHistoricalSnapshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ai-read-test-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, RideDatabase::class.java, name).build()
        try {
            val id = db.rides().insertSession(RideSession(startTimeEpochMillis = 1, durationMillis = 1000,
                status = StoredRideStatus.COMPLETED, activeSlot = null, analyticsFtpWatts = 130, bodyWeightKg = 60.0))
            db.rides().insertSample(RideSample(id, 1, 0, 0, null, null, 100))
            val rides = RoomRideRepository(db.rides(), { 170 }, { 70.0 })
            val repository = AiRideDataRepository(db, rides, { 170 }, { 70.0 })
            val result = JSONObject(repository.query("SELECT r.analyticsFtpWatts AS historic, p.ftpWatts AS currentFtp, p.bodyWeightKg, s.trainerPowerWatts, s.heartRateBpm FROM ride_sessions r JOIN ride_samples s ON s.sessionId=r.id CROSS JOIN user_profile p"))
            val row = result.getJSONArray("rows").getJSONArray(0)
            assertEquals(130, row.getInt(0)); assertEquals(170, row.getInt(1)); assertEquals(70.0, row.getDouble(2), 0.0)
            assertEquals(0, row.getInt(3)); assertTrue(row.isNull(4))
            assertTrue(JSONObject(repository.query("DELETE FROM ride_sessions")).has("error"))
            assertTrue(JSONObject(repository.query("WITH x AS (SELECT 1) SELECT * FROM x")).has("rows"))
            assertEquals(1, db.rides().sessionCount())
            assertEquals(130, db.rides().session(id)?.analyticsFtpWatts)
            // Writes through the existing Room owner remain available after the AI reader closes.
            db.rides().insertSample(RideSample(id, 1001, 1000, 100, 80.0, 130, 100))
            assertEquals(2, db.rides().samples(id).size)
            val noProfile = AiRideDataRepository(db, rides, { null }, { null })
            val nulls = JSONObject(noProfile.query("SELECT * FROM user_profile")).getJSONArray("rows").getJSONArray(0)
            assertTrue(nulls.isNull(0)); assertTrue(nulls.isNull(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun readOnlySqliteHandleCannotWriteEvenWithoutTheSqlGuard() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ai-read-barrier-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, RideDatabase::class.java, name).build()
        try {
            val path = checkNotNull(withContext(Dispatchers.IO) { db.openHelper.readableDatabase.path })
            withContext(Dispatchers.IO) {
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { reader ->
                    assertTrue(reader.isReadOnly)
                    var blocked = false
                    try { reader.execSQL("DELETE FROM ride_sessions") } catch (_: android.database.sqlite.SQLiteException) { blocked = true }
                    assertTrue(blocked)
                }
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
