package com.alexshell.raspisanie.data

import android.content.Context
import java.io.File

/**
 * Общая логика «какие занятия у пользователя» — для экрана и для уведомлений,
 * которые работают без открытого приложения.
 */
object Repository {

    const val PREFS = "settings"

    /** Основное расписание (файл, который был всегда). */
    const val SLOT_MAIN = "main"

    /** Второе расписание — показывается только на вкладке «Неделя». */
    const val SLOT_SECOND = "second"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun importedFile(context: Context) = File(context.filesDir, "schedule.xlsx")

    fun secondFile(context: Context) = File(context.filesDir, "schedule2.xlsx")

    /** Файл расписания для слота. */
    fun fileFor(context: Context, slot: String): File =
        if (slot == SLOT_SECOND) secondFile(context) else importedFile(context)

    /** Какой слот сейчас основное расписание. */
    fun primarySlot(context: Context): String =
        prefs(context).getString("primary", SLOT_MAIN)?.takeIf { it == SLOT_SECOND } ?: SLOT_MAIN

    /** Слот второго расписания — всегда противоположный основному. */
    fun secondarySlot(context: Context): String =
        if (primarySlot(context) == SLOT_SECOND) SLOT_MAIN else SLOT_SECOND

    fun groupKey(slot: String) = "group_$slot"

    /**
     * Группа, выбранная для слота. В старых версиях группа хранилась в "group" —
     * считаем её группой основного слота, чтобы не терять настройки при обновлении.
     */
    fun groupFor(context: Context, slot: String): String? {
        val p = prefs(context)
        p.getString(groupKey(slot), null)?.let { return it }
        return if (slot == SLOT_MAIN) p.getString("group", null) else null
    }

    fun loadSchedule(context: Context): Schedule = loadSlot(context, primarySlot(context))

    fun loadSlot(context: Context, slot: String): Schedule {
        val f = fileFor(context, slot)
        require(f.exists()) { "Расписание ещё не загружено" }
        return f.inputStream().use { ScheduleParser.parse(it) }
    }

    /** Занятия группы с учётом ручных правок и выбранных дисциплин по выбору. */
    fun lessonsFor(
        sch: Schedule,
        group: String,
        edits: List<LessonEdit>,
        chosenElectives: Set<String>,
        stream: Int? = null,
    ): List<Lesson> {
        val chosen = chosenElectives.filter { it in sch.electives }.toSet()
        val mine = edits.filter { it.group == group }
        val replaced = mine.mapNotNull { it.origKey }.toSet()
        return (sch.lessonsFor(group).filter { it.key !in replaced } + mine.mapNotNull { it.lesson })
            .filter { it.elective == null || chosen.isEmpty() || it.elective in chosen }
            // Занятия чужого потока скрываем; пока поток не выбран — показываем все.
            .filter { it.stream == null || stream == null || it.stream == stream }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.date }, { it.start }, { it.end }))
    }

    /** Всё нужное для уведомлений, прочитанное с диска. */
    fun loadForBackground(context: Context): Pair<List<Lesson>, List<Homework>>? {
        val p = prefs(context)
        val slot = primarySlot(context)
        val group = groupFor(context, slot) ?: return null
        val sch = runCatching { loadSlot(context, slot) }.getOrNull() ?: return null
        val lessons = lessonsFor(
            sch, group,
            EditStore(context).load(),
            p.getStringSet("electives", emptySet()).orEmpty(),
            p.getInt("stream", 0).takeIf { it > 0 },
        )
        val hw = HomeworkStore(context).load().filter { it.group == group }
        return lessons to hw
    }
}
