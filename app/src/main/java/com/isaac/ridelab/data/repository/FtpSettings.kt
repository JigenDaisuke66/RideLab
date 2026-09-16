package com.isaac.ridelab.data.repository

import android.content.Context
import com.isaac.ridelab.analytics.TrainingAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FtpSettings(context: Context) {
    private val preferences = context.getSharedPreferences("training_settings", Context.MODE_PRIVATE)
    suspend fun read(): Int? = withContext(Dispatchers.IO) {
        if (preferences.contains("ftp_watts")) preferences.getInt("ftp_watts", 0).takeIf { it in 50..500 } else null
    }
    suspend fun save(value: Int?) = withContext(Dispatchers.IO) {
        require(TrainingAnalytics.validFtp(value))
        val editor = preferences.edit()
        if (value == null) editor.remove("ftp_watts") else editor.putInt("ftp_watts", value)
        check(editor.commit())
    }
}
