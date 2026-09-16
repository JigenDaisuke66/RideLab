package com.isaac.ridelab

import android.app.Application
import com.isaac.ridelab.locale.AppLocale

class RideLabApplication : Application() {
    val rideDatabase by lazy {
        androidx.room.Room.databaseBuilder(this, com.isaac.ridelab.data.local.RideDatabase::class.java, "ridelab.db").addMigrations(com.isaac.ridelab.data.local.RideDatabase.MIGRATION_1_2).build()
    }
    val ftpSettings by lazy { com.isaac.ridelab.data.repository.FtpSettings(this) }
    val rideRepository by lazy { com.isaac.ridelab.data.repository.RoomRideRepository(rideDatabase.rides(), ftpSettings::read) }
    override fun onCreate() {
        super.onCreate()
        // Seed the platform locale before the first Activity and its resources are created.
        AppLocale.initialize(this)
    }
}
