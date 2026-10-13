package com.alexshell.raspisanie.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Локаль выбранного языка интерфейса (названия месяцев и дней недели). */
val RU: Locale get() = I18n.locale

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

fun LocalDate.dayMonth(): String = when (I18n.lang) {
    "zh" -> format(DateTimeFormatter.ofPattern("M月d日", RU))
    "en" -> format(DateTimeFormatter.ofPattern("MMMM d", RU))
    else -> format(DateTimeFormatter.ofPattern("d MMMM", RU))
}

fun LocalDate.weekdayFull(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() }

fun LocalDate.weekdayShort(): String {
    val s = dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.uppercase() }.trimEnd('.')
    return if (I18n.lang == "ru") s.take(2) else s
}

fun LocalDateTime.clock(): String = format(clock)

/** «Сегодня», «Завтра», «Пн, 12 октября». */
fun LocalDate.relative(today: LocalDate): String = when (this) {
    today -> tr("Сегодня")
    today.plusDays(1) -> tr("Завтра")
    today.minusDays(1) -> tr("Вчера")
    else -> "${weekdayShort()}, ${dayMonth()}"
}

/** Таймер: «1:05:09» или «05:09». */
fun Duration.timer(): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Человеческое «через 2 дн 3 ч» для долгих интервалов. */
fun Duration.humanize(): String {
    val s = seconds.coerceAtLeast(0)
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    return when {
        d > 0 -> "$d ${plural(d, "день", "дня", "дней")}" + if (h > 0) " " + tr("{0} ч", h) else ""
        h > 0 -> tr("{0} ч {1} мин", h, m)
        else -> tr("{0} мин", m)
    }
}

fun plural(n: Long, one: String, few: String, many: String): String = I18n.plural(n, one, few, many)
