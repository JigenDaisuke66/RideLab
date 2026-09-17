package com.isaac.ridelab

import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.data.repository.ErgTargetSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ErgTargetSettingsTest {
    @Test fun independentTrainerPreferencesSurviveNewSettingsInstance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val a = "test-erg-a"; val b = "test-erg-b"
        val prefs = context.getSharedPreferences("erg_targets", 0)
        prefs.edit().remove(a.uppercase()).remove(b.uppercase()).commit()
        try {
            val first = ErgTargetSettings(context)
            assertNull(first.read(a))
            first.save(a, 135); first.save(b, 100)
            val restored = ErgTargetSettings(context)
            assertEquals(135, restored.read(a)); assertEquals(100, restored.read(b))
        } finally { prefs.edit().remove(a.uppercase()).remove(b.uppercase()).commit() }
    }
}
