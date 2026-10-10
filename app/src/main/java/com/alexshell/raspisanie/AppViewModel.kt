package com.alexshell.raspisanie

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.data.HomeworkStore
import com.alexshell.raspisanie.data.Lesson
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
        lessons = if (g == null) emptyList() else schedule?.lessonsFor(g).orEmpty()
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
