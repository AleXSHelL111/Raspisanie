package com.alexshell.raspisanie.data

import android.content.Context
import java.io.File

/**
 * Общая логика «какие занятия у пользователя» — для экрана и для уведомлений,
 * которые работают без открытого приложения.
 */
object Repository {

    const val PREFS = "settings"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun importedFile(context: Context) = File(context.filesDir, "schedule.xlsx")

    fun loadSchedule(context: Context): Schedule {
        val f = importedFile(context)
        require(f.exists()) { "Расписание ещё не загружено" }
        return f.inputStream().use { ScheduleParser.parse(it) }
    }

    /** Занятия группы с учётом ручных правок и выбранных дисциплин по выбору. */
    fun lessonsFor(sch: Schedule, group: String, edits: List<LessonEdit>, chosenElectives: Set<String>): List<Lesson> {
        val chosen = chosenElectives.filter { it in sch.electives }.toSet()
        val mine = edits.filter { it.group == group }
        val replaced = mine.mapNotNull { it.origKey }.toSet()
        return (sch.lessonsFor(group).filter { it.key !in replaced } + mine.mapNotNull { it.lesson })
            .filter { it.elective == null || chosen.isEmpty() || it.elective in chosen }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.date }, { it.start }, { it.end }))
    }

    /** Всё нужное для уведомлений, прочитанное с диска. */
    fun loadForBackground(context: Context): Pair<List<Lesson>, List<Homework>>? {
        val p = prefs(context)
        val group = p.getString("group", null) ?: return null
        val sch = runCatching { loadSchedule(context) }.getOrNull() ?: return null
        val lessons = lessonsFor(
            sch, group,
            EditStore(context).load(),
            p.getStringSet("electives", emptySet()).orEmpty(),
        )
        val hw = HomeworkStore(context).load().filter { it.group == group }
        return lessons to hw
    }
}
