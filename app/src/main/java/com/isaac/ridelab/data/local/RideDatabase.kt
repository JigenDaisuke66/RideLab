package com.isaac.ridelab.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.isaac.ridelab.data.model.RideSession
import com.isaac.ridelab.data.model.RideSample

@Database(entities = [RideSession::class, RideSample::class], version = 3, exportSchema = true)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rides(): RideDao
    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ride_sessions ADD COLUMN bodyWeightKg REAL")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ride_sessions ADD COLUMN analyticsFtpWatts INTEGER")
            }
        }
    }
}
