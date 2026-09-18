package com.isaac.ridelab.data.repository

import android.content.Context
import com.isaac.ridelab.analytics.BodyWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeightSettings(context: Context) {
    private val prefs = context.getSharedPreferences("training_settings", Context.MODE_PRIVATE)
    suspend fun read(): Double? = withContext(Dispatchers.IO) {
        prefs.getString("body_weight_kg", null)?.toDoubleOrNull()?.takeIf { BodyWeight.valid(it) }
    }
    suspend fun save(kg: Double?) = withContext(Dispatchers.IO) {
        require(BodyWeight.valid(kg))
        val edit = prefs.edit()
        if (kg == null) edit.remove("body_weight_kg") else edit.putString("body_weight_kg", kg.toString())
        check(edit.commit())
    }
}
