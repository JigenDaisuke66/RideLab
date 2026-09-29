package com.isaac.ridelab.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/** Android 13+ owns the locale after one-time initialization; older Android uses our preference. */
object AppLocale {
    const val CHINESE = "zh-CN"
    const val ENGLISH = "en"
    internal const val PREFERENCES = "app_locale"
    internal const val LEGACY_LANGUAGE = "legacy_language"
    internal const val PLATFORM_INITIALIZED = "platform_initialized"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(PLATFORM_INITIALIZED, false)) {
                val manager = context.getSystemService(LocaleManager::class.java)
                // Preserve an existing Android choice (including one restored from backup).
                if (manager.applicationLocales.isEmpty) {
                    val initial = preferences.getString(LEGACY_LANGUAGE, CHINESE) ?: CHINESE
                    manager.applicationLocales = LocaleList.forLanguageTags(initial)
                }
                // Do not reseed on subsequent launches, even if the user selects System default.
                preferences.edit { putBoolean(PLATFORM_INITIALIZED, true) }
            }
        } else {
            initializeLegacy(context)
        }
    }

    internal fun initializeLegacy(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(LEGACY_LANGUAGE)) {
            preferences.edit { putString(LEGACY_LANGUAGE, CHINESE) }
        }
    }

    fun activityContext(context: Context): Context =
        if (Build.VERSION.SDK_INT >= 33) context else legacyContext(context)

    /** Window contexts may carry display/system locales rather than the per-app choice. */
    fun overlayContext(windowContext: Context, appContext: Context): Context {
        if (Build.VERSION.SDK_INT < 33) return legacyContext(windowContext)
        val manager = appContext.getSystemService(LocaleManager::class.java)
        val locales = manager.applicationLocales.takeUnless { it.isEmpty } ?: manager.systemLocales
        val configuration = Configuration(windowContext.resources.configuration)
        configuration.setLocales(locales)
        return windowContext.createConfigurationContext(configuration)
    }

    internal fun legacyContext(context: Context): Context {
        val language = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_LANGUAGE, CHINESE) ?: CHINESE
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(language))
        return context.createConfigurationContext(configuration)
    }

    /** Returns true only when the legacy Activity needs explicit recreation. */
    fun select(context: Context, language: String): Boolean {
        require(language == CHINESE || language == ENGLISH)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language)
            return false // Android performs the configuration change and persists the selection.
        }
        return selectLegacy(context, language)
    }

    internal fun selectLegacy(context: Context, language: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(LEGACY_LANGUAGE, CHINESE) == language) return false
        preferences.edit { putString(LEGACY_LANGUAGE, language) }
        return true
    }
}
