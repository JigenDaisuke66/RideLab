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
        assertEquals("Appareils", title("fr-FR"))
        assertEquals("デバイス", title("ja"))
        assertEquals("裝置", title("zh-TW"))
        assertEquals("Dispositivos", title("es"))
        assertEquals("Dispositivos", title("pt-BR"))
        assertEquals("设备", title("de-DE"))
    }
}
