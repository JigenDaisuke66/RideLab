package com.isaac.ridelab.locale

import org.junit.Assert.*
import org.junit.Test

class LanguagePolicyTest {
    @Test fun freshInstallFollowsSystemForEverySupportedLanguage() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertEquals(it, LanguagePolicy.resolve(AppLanguage.fromTag(null), listOf(it.tag)))
        }
    }
    @Test fun unsupportedSystemsFallBackToChinese() {
        assertEquals(AppLanguage.CHINESE, LanguagePolicy.resolve(AppLanguage.SYSTEM, listOf("de-DE")))
        assertEquals(AppLanguage.CHINESE, LanguagePolicy.resolve(AppLanguage.SYSTEM, emptyList()))
    }
    @Test fun supportedRegionalVariantsAndChineseScriptsResolveCorrectly() {
        mapOf("en-GB" to AppLanguage.ENGLISH, "es-MX" to AppLanguage.SPANISH,
            "fr-CA" to AppLanguage.FRENCH, "pt-PT" to AppLanguage.PORTUGUESE,
            "zh-Hant-HK" to AppLanguage.TRADITIONAL_CHINESE, "zh-TW" to AppLanguage.TRADITIONAL_CHINESE,
            "zh-Hans-SG" to AppLanguage.CHINESE).forEach { (tag, expected) ->
            assertEquals(expected, LanguagePolicy.resolve(AppLanguage.SYSTEM, listOf(tag)))
        }
    }
    @Test fun manualChoiceWinsAcrossSystemChangesAndStoredTagReload() {
        val saved = AppLanguage.JAPANESE.tag
        assertEquals(AppLanguage.JAPANESE, LanguagePolicy.resolve(AppLanguage.fromTag(saved), listOf("fr")))
        assertEquals(AppLanguage.JAPANESE, LanguagePolicy.resolve(AppLanguage.fromTag(saved), listOf("es")))
        assertEquals(AppLanguage.ENGLISH, LanguagePolicy.resolve(AppLanguage.ENGLISH, listOf("ja")))
    }
    @Test fun systemSelectionAndLegacyChoicesRemainCompatible() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.FRENCH, LanguagePolicy.resolve(AppLanguage.fromTag(""), listOf("fr")))
        assertEquals(AppLanguage.SPANISH, LanguagePolicy.resolve(AppLanguage.fromTag(null), listOf("es")))
    }
}
