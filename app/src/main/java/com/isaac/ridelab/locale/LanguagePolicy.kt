package com.isaac.ridelab.locale

import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM(""), CHINESE("zh-CN"), ENGLISH("en"), JAPANESE("ja"),
    TRADITIONAL_CHINESE("zh-TW"), SPANISH("es"), PORTUGUESE("pt-BR"), FRENCH("fr");
    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag.equals(tag, true) }
            ?: matching(tag) ?: SYSTEM
        fun matching(tag: String?): AppLanguage? {
            val locale = Locale.forLanguageTag(tag.orEmpty())
            return when (locale.language) {
                "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) TRADITIONAL_CHINESE else CHINESE
                "en" -> ENGLISH; "ja" -> JAPANESE; "es" -> SPANISH
                "pt" -> PORTUGUESE; "fr" -> FRENCH; else -> null
            }
        }
    }
}

object LanguagePolicy {
    fun resolve(selection: AppLanguage, systemTags: List<String>): AppLanguage =
        if (selection != AppLanguage.SYSTEM) selection
        else systemTags.firstNotNullOfOrNull(AppLanguage::matching) ?: AppLanguage.CHINESE
}
