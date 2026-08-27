package com.pourista.core

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Язык интерфейса.
 *
 * С Android 13 языком приложения ведает система: выбор уходит в [LocaleManager],
 * тот сам пересоздаёт экраны, и тот же язык виден в системных настройках
 * приложения. На более старых версиях такого механизма нет, поэтому тег
 * храним сами и подменяем локаль в конфигурации у каждого контекста, из
 * которого читаются строки.
 */
object AppLocale {

    /** Язык из списка выбора. Подпись — на нём же: её не переводят. */
    data class Language(val tag: String, val label: String)

    /**
     * Языки, на которые переведено приложение. Порядок — латиница, кириллица,
     * иероглифика; внутри по алфавиту.
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

    /** Выбранный язык или null, когда приложение идёт за системой. */
    fun selected(context: Context): Language? {
        val tag = storedTag(context) ?: return null
        return languages.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: languages.firstOrNull { it.tag.language().equals(tag.language(), ignoreCase = true) }
    }

    /** Запоминает выбор и применяет его. null — вернуться к языку системы. */
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
     * Контекст, из которого строки читаются на выбранном языке.
     *
     * До Android 13 это единственный способ дотянуться до ресурсов: система
     * о выборе не знает. Заодно выравнивает [Locale.getDefault] — по нему
     * форматируются даты. С Android 13 всё уже сделано системой, и контекст
     * возвращается как есть.
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
     * Отдельный маленький файл, а не общее хранилище настроек: язык читается
     * в attachBaseContext, до того как приложение вообще собрано, и ждать
     * там асинхронный DataStore нечем.
     */
    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

    private fun String.language(): String = substringBefore('-')

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"
}
