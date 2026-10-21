package com.isaac.ridelab

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.repository.*
import org.junit.Assert.*
import org.junit.Test

class OverlayPreferencesTest {
    @Test fun migratesLegacyWidthAndPersistsUnifiedScaleWithoutAConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
        val original = prefs.all
        try {
            prefs.edit().clear().putInt("width_percent", 160).putInt("height_percent", 40)
                .putInt("font_percent", 80).putInt("transparency", 30).commit()
            val store = OverlayPreferencesStore(context)
            assertEquals(OverlayPreferences(30, 140), store.read())
            store.save(OverlayPreferences(45, 75))
            assertEquals(OverlayPreferences(45, 75), OverlayPreferencesStore(context).read())
        } finally {
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.commit()
        }
    }
}
