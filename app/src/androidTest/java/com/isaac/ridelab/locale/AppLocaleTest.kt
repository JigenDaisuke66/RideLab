package com.isaac.ridelab.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.isaac.ridelab.MainActivity
import com.isaac.ridelab.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class AppLocaleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences(AppLocale.PREFERENCES, Context.MODE_PRIVATE)
    private var previousLegacy: String? = null
    private var previousInitialized = false
    private var previousLocales: LocaleList? = null

    @Before fun isolateLocalePreferences() {
        previousLegacy = preferences.getString(AppLocale.LEGACY_LANGUAGE, null)
        previousInitialized = preferences.getBoolean(AppLocale.PLATFORM_INITIALIZED, false)
        if (Build.VERSION.SDK_INT >= 33) previousLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        assertTrue(preferences.edit().clear().commit())
    }

    @After fun restoreLocalePreferences() {
        assertTrue(preferences.edit().clear()
            .putString(AppLocale.LEGACY_LANGUAGE, previousLegacy)
            .putBoolean(AppLocale.PLATFORM_INITIALIZED, previousInitialized).commit())
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = previousLocales!!
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test fun legacyFreshInstallKeepsFollowSystemWithoutWritingAPreference() {
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        assertEquals("Devices", english.getString(R.string.devices_title))
        AppLocale.initializeLegacy(english)
        assertNull(preferences.getString(AppLocale.LEGACY_LANGUAGE, null))
    }

    @Test fun legacyExplicitEnglishSurvivesInitializationAndNewContexts() {
        AppLocale.initializeLegacy(context)
        assertTrue(AppLocale.selectLegacy(context, AppLocale.ENGLISH))
        AppLocale.initializeLegacy(context)
        val newContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
        assertEquals("en", preferences.getString(AppLocale.LEGACY_LANGUAGE, null))
        assertEquals("Devices", AppLocale.legacyContext(newContext).getString(R.string.devices_title))
        assertFalse(AppLocale.selectLegacy(newContext, AppLocale.ENGLISH))
    }

    @Test fun platformFreshInstallFollowsSystemAndFirstActivityUsesResolvedLanguage() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocale.initialize(context)
        assertTrue(manager.applicationLocales.isEmpty)
        assertTrue(preferences.getBoolean(AppLocale.PLATFORM_INITIALIZED, false))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(AppLocale.effectiveLanguage(context).tag,
                it.resources.configuration.locales[0].toLanguageTag()) }
        }
    }

    @Test fun platformExistingExplicitChoiceIsNotOverwrittenOnFirstInitialization() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLocale.initialize(context)
        assertEquals("en", manager.applicationLocales.toLanguageTags())
    }

    @Test fun platformEnglishChoiceSurvivesInitializationRecreationAndRelaunch() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        AppLocale.initialize(context)
        assertFalse(AppLocale.select(context, AppLocale.ENGLISH))
        AppLocale.initialize(context)
        repeat(2) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.recreate()
                scenario.onActivity { assertEquals("Devices", it.getString(R.string.devices_title)) }
            }
        }
        assertEquals("en", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
    }

    @Test fun platformExplicitSystemDefaultIsNotResetToChineseOnNextInitialization() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        AppLocale.initialize(context)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocale.initialize(context)
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test fun overlayFollowsAppChoiceInsteadOfDisplayLocale() {
        val englishDisplay = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        AppLocale.select(context, AppLocale.CHINESE)
        assertEquals("关闭", AppLocale.overlayContext(englishDisplay, context).getString(R.string.overlay_close))
        AppLocale.select(context, AppLocale.ENGLISH)
        assertEquals("Close", AppLocale.overlayContext(englishDisplay, context).getString(R.string.overlay_close))
    }

    @Test fun overlayHonorsExplicitSystemDefault() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertEquals(AppLocale.effectiveLanguage(context).tag,
            AppLocale.overlayContext(context, context).resources.configuration.locales[0].toLanguageTag())
    }

    @Test fun upgradingFromLegacyMigratesSavedEnglishOnce() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        AppLocale.initializeLegacy(context)
        AppLocale.selectLegacy(context, AppLocale.ENGLISH)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocale.initialize(context)
        assertEquals("en", manager.applicationLocales.toLanguageTags())
    }

    @Test fun everyManualLanguageLocalizesOverlayCsvAndNotificationResources() {
        val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach { language ->
            AppLocale.select(context, language.tag)
            AppLocale.initialize(context) // Reload must not reset the override.
            assertEquals(language, AppLocale.selection(context))
            val activity = AppLocale.activityContext(context)
            val overlay = AppLocale.overlayContext(display, context)
            assertEquals(language.tag, overlay.resources.configuration.locales[0].toLanguageTag())
            assertEquals(activity.getString(R.string.overlay_close), overlay.getString(R.string.overlay_close))
            assertEquals(activity.getString(R.string.ride_active), overlay.getString(R.string.ride_active))
            assertEquals(12, activity.resources.getStringArray(R.array.ride_csv_headers).size)
            assertFalse(activity.getString(R.string.history_watts, 123.4).contains("%1$"))
        }
        AppLocale.select(context, "")
        AppLocale.initialize(context)
        assertEquals(AppLanguage.SYSTEM, AppLocale.selection(context))
    }
}
