package hr.bertlab.fluteassistant.ui.common

import java.util.Locale

/**
 * The languages the app ships, as offered by the in-app switch. [tag] is the BCP 47 tag the
 * platform's per-app language setting takes; [flag] and [endonym] label the switch without
 * needing a translation (a child who cannot read the current language still finds theirs).
 */
enum class AppLanguage(val tag: String, val flag: String, val endonym: String) {
    ENGLISH("en", "🇺🇸", "English"), // flag: United States
    CROATIAN("hr", "🇭🇷", "Hrvatski"); // flag: Croatia

    companion object {
        /** The language the app shows for [locale]: Croatian for hr, else English (the fallback resources). */
        fun fromLocale(locale: Locale): AppLanguage =
            if (locale.language == CROATIAN.tag) CROATIAN else ENGLISH
    }
}
