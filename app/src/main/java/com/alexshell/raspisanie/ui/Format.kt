package com.alexshell.raspisanie.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", RU)
private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

fun LocalDate.dayMonth(): String = format(dayMonth)

fun LocalDate.weekdayFull(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() }

fun LocalDate.weekdayShort(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.uppercase() }.take(2)

fun LocalDateTime.clock(): String = format(clock)

/** «Сегодня», «Завтра», «Пн, 12 октября». */
fun LocalDate.relative(today: LocalDate): String = when (this) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    today.minusDays(1) -> "Вчера"
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
        d > 0 -> "$d ${plural(d, "день", "дня", "дней")}" + if (h > 0) " $h ч" else ""
        h > 0 -> "$h ч $m мин"
        else -> "$m мин"
    }
}

fun plural(n: Long, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1L && n100 != 11L -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}
