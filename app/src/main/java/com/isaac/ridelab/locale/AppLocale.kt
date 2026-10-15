package com.isaac.ridelab.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/** Platform locales are the sole override store on Android 13+; legacy uses app_locale. */
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
                // Migrate an actual stored legacy choice only. Fresh installs stay Follow System.
                val legacy = preferences.getString(LEGACY_LANGUAGE, null)
                if (manager.applicationLocales.isEmpty && legacy != null)
                    manager.applicationLocales = LocaleList.forLanguageTags(AppLanguage.fromTag(legacy).tag)
                preferences.edit { putBoolean(PLATFORM_INITIALIZED, true) }
            }
        }
    }

    internal fun initializeLegacy(context: Context) = Unit // Absence means Follow System.

    fun selection(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= 33) AppLanguage.fromTag(
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(','))
        else AppLanguage.fromTag(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_LANGUAGE, null))

    fun effectiveLanguage(context: Context): AppLanguage {
        val locales = if (Build.VERSION.SDK_INT >= 33)
            context.getSystemService(LocaleManager::class.java).systemLocales
        else Resources.getSystem().configuration.locales
        return LanguagePolicy.resolve(selection(context), (0 until locales.size()).map { locales[it].toLanguageTag() })
    }

    fun activityContext(context: Context): Context = localized(context, context)

    fun overlayContext(windowContext: Context, appContext: Context): Context = localized(windowContext, appContext)

    internal fun legacyContext(context: Context): Context {
        val preference = AppLanguage.fromTag(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_LANGUAGE, null))
        val system = Resources.getSystem().configuration.locales
        val language = LanguagePolicy.resolve(preference, (0 until system.size()).map { system[it].toLanguageTag() })
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language.tag))
        })
    }

    private fun localized(base: Context, owner: Context): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(effectiveLanguage(owner).tag))
        return base.createConfigurationContext(configuration)
    }

    /** True only when legacy Activity recreation must be requested by the caller. */
    fun select(context: Context, language: String): Boolean {
        require(AppLanguage.entries.any { it.tag == language })
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
            return false
        }
        return selectLegacy(context, language)
    }

    internal fun selectLegacy(context: Context, language: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (AppLanguage.fromTag(preferences.getString(LEGACY_LANGUAGE, null)).tag == language) return false
        val previous = activityContext(context)
        preferences.edit {
            if (language.isEmpty()) remove(LEGACY_LANGUAGE) else putString(LEGACY_LANGUAGE, language)
        }
        // Legacy Android does not dispatch a locale configuration change to a running service.
        LocalizedRideNotification.refresh(context, previous, activityContext(context))
        return true
    }
}
