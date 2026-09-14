package com.pourista.core

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The interface language.
 *
 * From Android 13 on the app language is the system's business: the choice goes to [LocaleManager],
 * which recreates the screens itself, and the same language shows in the system settings of the app.
 * Older versions have no such mechanism, so we keep the tag ourselves and substitute the locale in
 * the configuration of every context the strings are read from.
 */
object AppLocale {

    /** A language from the picker. The label is in that language: it is not translated. */
    data class Language(val tag: String, val label: String)

    /**
     * The languages the app is translated into. The order is Latin, Cyrillic, ideographic; inside
     * that, alphabetical.
     */
    val languages: List<Language> = listOf(
        Language("en", "English"),
        Language("de", "Deutsch"),
        Language("es", "Español"),
        Language("fr", "Français"),
        Language("it", "Italiano"),
        Language("nl", "Nederlands"),
        Language("pl", "Polski"),
        Language("pt", "Português"),
        Language("tr", "Türkçe"),
        Language("ru", "Русский"),
        Language("uk", "Українська"),
        Language("ja", "日本語"),
        Language("ko", "한국어"),
        Language("zh-CN", "简体中文"),
    )

    /** The chosen language, or null when the app follows the system. */
    fun selected(context: Context): Language? {
        val tag = storedTag(context) ?: return null
        return languages.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: languages.firstOrNull { it.tag.language().equals(tag.language(), ignoreCase = true) }
    }

    /** Remembers the choice and applies it. null means going back to the system language. */
    fun apply(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return
        }
        context.prefs().edit().apply {
            if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }.apply()
        Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: systemLocale())
    }

    /**
     * A context the strings are read from in the chosen language.
     *
     * Before Android 13 this is the only way to reach the resources: the system knows nothing about
     * the choice. It also aligns [Locale.getDefault] — dates are formatted by it. From Android 13 on
     * everything is already done by the system, and the context comes back as it is.
     */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val locale = storedTag(context)?.let(Locale::forLanguageTag) ?: return context
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
        } else {
            context.prefs().getString(KEY_TAG, null)
        }

    /**
     * A small file of its own rather than the common settings storage: the language is read in
     * attachBaseContext, before the app is assembled at all, and there is nothing there to wait for
     * an asynchronous DataStore with.
     */
    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

    private fun String.language(): String = substringBefore('-')

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"
}
