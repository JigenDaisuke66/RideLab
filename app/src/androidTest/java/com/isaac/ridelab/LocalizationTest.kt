package com.isaac.ridelab

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @Test fun chineseIsFallbackAndEnglishRemainsAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun title(tag: String): String {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return context.createConfigurationContext(configuration).getString(R.string.devices_title)
        }
        assertEquals("设备", title("zh-CN"))
        assertEquals("Devices", title("en"))
        assertEquals("设备", title("fr-FR"))
    }
}
