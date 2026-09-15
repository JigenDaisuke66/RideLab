package com.isaac.ridelab

import android.app.Application
import com.isaac.ridelab.locale.AppLocale

class RideLabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Seed the platform locale before the first Activity and its resources are created.
        AppLocale.initialize(this)
    }
}
