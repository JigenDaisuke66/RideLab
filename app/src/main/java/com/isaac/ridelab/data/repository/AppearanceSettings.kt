package com.isaac.ridelab.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.isaac.ridelab.data.model.Appearance

/** Lightweight presentation preferences, independent of the ride database/service. */
class AppearanceSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun read() = Appearance.restore(preferences.getString("theme", null),
        preferences.getString("overlay_palette", null), preferences.getString("overlay_style", null))
    fun save(value: Appearance) {
        preferences.edit().putString("theme", value.theme.name)
            .putString("overlay_palette", value.overlayPalette?.name)
            .putString("overlay_style", value.overlayStyle.name).apply()
    }
    fun observe(update: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> update() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
