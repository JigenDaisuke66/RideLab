package com.isaac.ridelab.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile

class ErgTargetSettings(context: Context) {
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("erg_targets", Context.MODE_PRIVATE)
    private fun key(address: String) = address.uppercase(Locale.ROOT)
    suspend fun read(address: String): Int? = mutex.withLock { withContext(Dispatchers.IO) {
        if (preferences.contains(key(address))) preferences.getInt(key(address), 100) else null
    } }
    suspend fun save(address: String, watts: Int) = mutex.withLock { withContext(Dispatchers.IO) {
        require(watts in ThinkRiderX2MaxProfile.minErgWatts..ThinkRiderX2MaxProfile.maxErgWatts)
        check(preferences.edit().putInt(key(address), watts).commit())
    } }
}
