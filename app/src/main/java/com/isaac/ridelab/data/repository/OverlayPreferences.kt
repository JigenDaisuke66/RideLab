package com.isaac.ridelab.data.repository

import android.content.Context

data class OverlayPreferences(val transparency: Int = 6, val font: Int = 100, val width: Int = 100, val height: Int = 100)
class OverlayPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
    fun read() = OverlayPreferences(prefs.getInt("transparency", 6).coerceIn(0,80), prefs.getInt("font_percent",100).coerceIn(80,140),
        prefs.getInt("width_percent",100).coerceIn(80,160), prefs.getInt("height_percent",100).coerceIn(40,140))
    fun save(value: OverlayPreferences) { prefs.edit().putInt("transparency",value.transparency.coerceIn(0,80))
        .putInt("font_percent",value.font.coerceIn(80,140)).putInt("width_percent",value.width.coerceIn(80,160))
        .putInt("height_percent",value.height.coerceIn(40,140)).apply() }
}
