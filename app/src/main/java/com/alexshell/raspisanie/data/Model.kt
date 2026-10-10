package com.alexshell.raspisanie.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Отрезок времени в минутах от полуночи. */
data class TimeSlot(val start: Int, val end: Int) {
    val startTime: LocalTime get() = LocalTime.of(start / 60, start % 60)
    val endTime: LocalTime get() = LocalTime.of(end / 60, end % 60)
    override fun toString() = "${fmt(start)}–${fmt(end)}"

    companion object {
        fun fmt(m: Int) = "%d:%02d".format(m / 60, m % 60)
    }
}

enum class LessonKind(val title: String) {
    LECTURE("Лекция"),
    PRACTICE("Практика"),
    ELECTIVE("По выбору"),
}

data class Lesson(
    val date: LocalDate,
    val slots: List<TimeSlot>,
    /** Короткое название предмета — им же связываются лекции, практики и ДЗ. */
    val subject: String,
    val fullSubject: String,
    val kind: LessonKind,
    val groups: Set<String>,
    val location: String?,
    val details: String,
    val online: Boolean,
    /** Время отличается от обычного для этого предмета. */
    val specialTime: Boolean,
    /** Название дисциплины по выбору, если занятие относится к одной из них. */
    val elective: String? = null,
    /** Занятие добавлено или изменено вручную (id правки). */
    val editId: Long? = null,
) {
    val start: Int get() = slots.first().start
    val end: Int get() = slots.last().end
    val startAt: LocalDateTime get() = date.atTime(slots.first().startTime)
    val endAt: LocalDateTime get() = date.atTime(slots.last().endTime)
    val timeText: String get() = "${TimeSlot.fmt(start)}–${TimeSlot.fmt(end)}"

    /** Стабильный ключ занятия (для привязки домашних заданий). */
    val key: String get() = "$subject|$date|$start|${kind.name}"
}

data class DisciplineInfo(
    val name: String,
    val department: String,
    val attestation: String?,
)

data class Schedule(
    val title: String,
    val groups: List<String>,
    val lessons: List<Lesson>,
    val semesterStart: LocalDate,
    val week1Ranges: List<ClosedRange<LocalDate>>,
    val week2Ranges: List<ClosedRange<LocalDate>>,
    val disciplines: List<DisciplineInfo>,
    /** Все дисциплины по выбору, найденные в файле. */
    val electives: List<String> = emptyList(),
) {
    /** Номер учебной недели (1 или 2). */
    fun weekType(date: LocalDate): Int {
        if (week1Ranges.any { date in it }) return 1
        if (week2Ranges.any { date in it }) return 2
        val base = (week1Ranges.firstOrNull()?.start ?: semesterStart).with(java.time.DayOfWeek.MONDAY)
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(base, date.with(java.time.DayOfWeek.MONDAY))
        return if (Math.floorMod(weeks, 2L) == 0L) 1 else 2
    }

    fun lessonsFor(group: String): List<Lesson> =
        lessons.filter { group in it.groups }.distinctBy { it.key }.sortedWith(compareBy({ it.date }, { it.start }, { it.end }))

    fun disciplineFor(subject: String): DisciplineInfo? {
        val key = ScheduleParser.normKey(subject)
        val names = disciplines.associateWith { ScheduleParser.normKey(it.name) }
        return names.entries.firstOrNull { (_, n) -> n.startsWith(key) || key.startsWith(n) }?.key
            ?: names.entries.firstOrNull { (_, n) -> n.length >= 7 && key.length >= 7 && n.take(7) == key.take(7) }?.key
    }
}
