package com.isaac.ridelab.data.repository

import android.content.Context

data class OverlayPreferences(val transparency: Int = 6, val size: Int = 100) {
    companion object {
        fun restore(transparency: Int, size: Int) = OverlayPreferences(transparency.coerceIn(0, 80), size.coerceIn(60, 140))
    }
}
class OverlayPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
    fun read() = OverlayPreferences.restore(prefs.getInt("transparency", 6),
        prefs.getInt("scale_percent", prefs.getInt("width_percent", 100)))
    fun save(value: OverlayPreferences) {
        val safe = OverlayPreferences.restore(value.transparency, value.size)
        prefs.edit().putInt("transparency", safe.transparency).putInt("scale_percent", safe.size).apply()
    }
}
