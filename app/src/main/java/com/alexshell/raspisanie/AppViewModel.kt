package com.alexshell.raspisanie

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.data.EditStore
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.data.HomeworkStore
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.LessonEdit
import com.alexshell.raspisanie.data.Schedule
import com.alexshell.raspisanie.data.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val hwStore = HomeworkStore(app)
    private val editStore = EditStore(app)
    private val importedFile = File(app.filesDir, "schedule.xlsx")

    var schedule by mutableStateOf<Schedule?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var group by mutableStateOf(prefs.getString("group", null))
        private set
    var homework by mutableStateOf(hwStore.load())
        private set
    var message by mutableStateOf<String?>(null)
    var edits by mutableStateOf(editStore.load())
        private set
    /** Выбранные дисциплины по выбору (пусто — показывать все). */
    var chosenElectives by mutableStateOf(prefs.getStringSet("electives", emptySet())!!.toSet())
        private set

    val isImported: Boolean get() = importedFile.exists()

    /** Занятия выбранной группы, отсортированные по времени. */
    var lessons by mutableStateOf<List<Lesson>>(emptyList())
        private set

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = if (importedFile.exists()) importedFile.inputStream()
                    else getApplication<Application>().assets.open("schedule.xlsx")
                    stream.use { ScheduleParser.parse(it) }
                }
            }
            result.onSuccess {
                schedule = it
                loadError = null
                if (group != null && group !in it.groups) group = null
                refreshLessons()
            }.onFailure {
                loadError = "Не удалось прочитать расписание: ${it.message}"
            }
        }
    }

    private fun refreshLessons() {
        val g = group
        val sch = schedule
        if (g == null || sch == null) {
            lessons = emptyList(); return
        }
        val chosen = chosenElectives.filter { it in sch.electives }.toSet()
        val mine = edits.filter { it.group == g }
        val replaced = mine.mapNotNull { it.origKey }.toSet()
        lessons = (sch.lessonsFor(g).filter { it.key !in replaced } + mine.mapNotNull { it.lesson })
            .filter { it.elective == null || chosen.isEmpty() || it.elective in chosen }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.date }, { it.start }, { it.end }))
    }

    // ---------- Дисциплины по выбору ----------

    fun toggleElective(name: String) {
        chosenElectives = if (name in chosenElectives) chosenElectives - name else chosenElectives + name
        prefs.edit().putStringSet("electives", chosenElectives).apply()
        refreshLessons()
    }

    fun clearElectives() {
        chosenElectives = emptySet()
        prefs.edit().putStringSet("electives", emptySet()).apply()
        refreshLessons()
    }

    // ---------- Редактор занятий ----------

    /** Такие же пары: тот же предмет, тип, день недели и время, начиная с этой даты. */
    fun similarLessons(l: Lesson): List<Lesson> = lessons.filter {
        it.subject == l.subject && it.kind == l.kind && it.start == l.start &&
            it.date.dayOfWeek == l.date.dayOfWeek && it.date >= l.date
    }

    /**
     * Сохраняет изменённое занятие. [original] == null — новое занятие.
     * [applyAll] — применить изменения (кроме даты) ко всем таким же парам.
     * [repeatUntil] — для нового занятия: повторять еженедельно до этой даты.
     */
    fun saveLesson(original: Lesson?, edited: Lesson, applyAll: Boolean, repeatUntil: LocalDate?) {
        val g = group ?: return
        var list = edits
        var id = System.currentTimeMillis()
        if (original == null) {
            var d = edited.date
            val last = repeatUntil ?: edited.date
            while (!d.isAfter(last)) {
                list = list + LessonEdit(id, g, null, edited.copy(date = d, groups = setOf(g), editId = id))
                id++
                d = d.plusWeeks(1)
            }
        } else {
            val targets = if (applyAll) similarLessons(original) else listOf(original)
            targets.forEach { t ->
                val newLesson = edited.copy(
                    date = if (t === original || t.key == original.key) edited.date else t.date,
                    groups = setOf(g),
                    specialTime = false,
                )
                list = upsert(list, g, t, newLesson, id++)
                moveHomework(t.key, newLesson)
            }
        }
        updateEdits(list)
        message = if (original == null) "Занятие добавлено" else "Изменения сохранены"
    }

    fun deleteLesson(lesson: Lesson, applyAll: Boolean) {
        val g = group ?: return
        var list = edits
        var id = System.currentTimeMillis()
        val targets = if (applyAll) similarLessons(lesson) else listOf(lesson)
        targets.forEach { list = upsert(list, g, it, null, id++) }
        updateEdits(list)
        message = if (targets.size > 1) "Удалено занятий: ${targets.size}" else "Занятие удалено"
    }

    /** Заменяет занятие [target] на [newLesson] (null — удалить). */
    private fun upsert(list: List<LessonEdit>, g: String, target: Lesson, newLesson: Lesson?, id: Long): List<LessonEdit> {
        val existing = target.editId?.let { eid -> list.firstOrNull { it.id == eid } }
        return if (existing != null) {
            if (newLesson == null && existing.origKey == null) list - existing
            else list.map { if (it.id == existing.id) it.copy(lesson = newLesson?.copy(editId = existing.id)) else it }
        } else {
            list + LessonEdit(id, g, target.key, newLesson?.copy(editId = id))
        }
    }

    private fun moveHomework(oldKey: String, l: Lesson) {
        if (oldKey == l.key) return
        updateHomework(homework.map {
            if (it.group == group && it.lessonKey == oldKey)
                it.copy(lessonKey = l.key, subject = l.subject, dueDate = l.date, dueStart = l.start)
            else it
        })
    }

    fun resetEdits() {
        updateEdits(edits.filterNot { it.group == group })
        message = "Ручные правки сброшены"
    }

    fun editsCount(): Int = edits.count { it.group == group }

    private fun updateEdits(list: List<LessonEdit>) {
        edits = list
        editStore.save(list)
        refreshLessons()
    }

    fun selectGroup(g: String) {
        group = g
        prefs.edit().putString("group", g).apply()
        refreshLessons()
    }

    /** Импорт нового файла: сначала проверяем, что он разбирается, затем сохраняем. */
    fun importSchedule(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Файл недоступен")
                    val parsed = ScheduleParser.parse(bytes.inputStream())
                    require(parsed.lessons.isNotEmpty()) { "В файле не найдено ни одного занятия" }
                    importedFile.writeBytes(bytes)
                    parsed
                }
            }
            result.onSuccess {
                schedule = it
                if (group != null && group !in it.groups) group = null
                refreshLessons()
                message = "Расписание обновлено: ${it.lessons.size} занятий"
            }.onFailure {
                message = "Ошибка импорта: ${it.message}"
            }
        }
    }

    fun resetToBuiltIn() {
        importedFile.delete()
        reload()
        message = "Восстановлено встроенное расписание"
    }

    fun lessonsOn(date: LocalDate): List<Lesson> = lessons.filter { it.date == date }

    /** Следующее занятие по тому же предмету (предпочтительно того же типа). */
    fun nextOccurrence(lesson: Lesson): Lesson? {
        val later = lessons.filter { it.subject == lesson.subject && it.startAt > lesson.startAt }
        return later.firstOrNull { it.kind == lesson.kind } ?: later.firstOrNull()
    }

    /** Ближайшее будущее занятие по предмету — для добавления ДЗ из списка. */
    fun nextBySubject(subject: String, now: LocalDateTime = LocalDateTime.now()): Lesson? {
        val later = lessons.filter { it.subject == subject && it.startAt > now }
        return later.firstOrNull { it.kind != com.alexshell.raspisanie.data.LessonKind.LECTURE } ?: later.firstOrNull()
    }

    fun subjects(): List<String> = lessons.map { it.subject }.distinct().sorted()

    fun homeworkFor(lesson: Lesson): List<Homework> =
        homework.filter { it.group == group && it.lessonKey == lesson.key }

    fun groupHomework(): List<Homework> = homework.filter { it.group == group }

    fun addHomework(target: Lesson, text: String) {
        val g = group ?: return
        val hw = Homework(
            id = System.currentTimeMillis(),
            group = g,
            lessonKey = target.key,
            subject = target.subject,
            dueDate = target.date,
            dueStart = target.start,
            text = text.trim(),
        )
        updateHomework(homework + hw)
    }

    fun toggleDone(id: Long) = updateHomework(homework.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun deleteHomework(id: Long) = updateHomework(homework.filterNot { it.id == id })

    fun editHomework(id: Long, text: String) =
        updateHomework(homework.map { if (it.id == id) it.copy(text = text.trim()) else it })

    private fun updateHomework(list: List<Homework>) {
        homework = list
        hwStore.save(list)
    }

    fun lessonByKey(key: String): Lesson? = lessons.firstOrNull { it.key == key }
}
