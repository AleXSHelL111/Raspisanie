package com.alexshell.raspisanie.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * Простая локализация: ключ — русский текст, переводы лежат в assets/i18n.json
 * (тот же словарь использует веб-версия). Подстановки — {0}, {1}, …
 */
object I18n {
    data class Lang(val code: String, val name: String, val flag: String)

    val languages = listOf(
        Lang("ru", "Русский", "🇷🇺"),
        Lang("en", "English", "🇬🇧"),
        Lang("fr", "Français", "🇫🇷"),
        Lang("de", "Deutsch", "🇩🇪"),
        Lang("es", "Español", "🇪🇸"),
        Lang("ar", "العربية", "🇸🇦"),
        Lang("zh", "中文", "🇨🇳"),
    )

    /** Текущий язык; смена мгновенно перерисовывает интерфейс. */
    var lang by mutableStateOf("ru")

    private var table: Map<String, Map<String, String>> = emptyMap()

    val isRtl: Boolean get() = lang == "ar"
    val locale: Locale get() = Locale.forLanguageTag(lang)

    /** Загрузить словарь и сохранённый язык (вызывается и из фоновых задач). */
    fun init(context: Context) {
        if (table.isEmpty()) {
            table = runCatching {
                val json = JSONObject(context.assets.open("i18n.json").bufferedReader().use { it.readText() })
                buildMap {
                    for (key in json.keys()) {
                        val o = json.getJSONObject(key)
                        put(key, buildMap { for (l in o.keys()) put(l, o.getString(l)) })
                    }
                }
            }.getOrDefault(emptyMap())
        }
        lang = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", null) ?: lang
    }

    fun set(context: Context, code: String) {
        lang = code
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
    }

    fun tr(key: String, vararg args: Any?): String {
        val l = lang
        var s = if (l == "ru") key else table[key]?.get(l) ?: key
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }

    /** Множественное число: для русского — три формы, для остальных — «один|много» из словаря по ключу [many]. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        if (lang == "ru") {
            val n10 = n % 10
            val n100 = n % 100
            return when {
                n10 == 1L && n100 != 11L -> one
                n10 in 2..4 && n100 !in 12..14 -> few
                else -> many
            }
        }
        val forms = (table[many]?.get(lang) ?: many).split('|')
        return if (n == 1L || forms.size == 1) forms.first() else forms[1]
    }
}

fun tr(key: String, vararg args: Any?): String = I18n.tr(key, *args)
