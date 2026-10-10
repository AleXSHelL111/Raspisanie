package com.alexshell.raspisanie.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Ручная правка расписания.
 *  - origKey != null, lesson != null — занятие из файла заменено;
 *  - origKey != null, lesson == null — занятие из файла скрыто (удалено);
 *  - origKey == null, lesson != null — добавлено новое занятие.
 */
data class LessonEdit(
    val id: Long,
    val group: String,
    val origKey: String?,
    val lesson: Lesson?,
)

class EditStore(context: Context) {
    private val prefs = context.getSharedPreferences("edits", Context.MODE_PRIVATE)

    fun load(): List<LessonEdit> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val group = o.getString("group")
                LessonEdit(
                    id = o.getLong("id"),
                    group = group,
                    origKey = o.optString("origKey").ifEmpty { null },
                    lesson = o.optJSONObject("lesson")?.let { lessonFrom(it, group, o.getLong("id")) },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<LessonEdit>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("group", e.group)
                    .put("origKey", e.origKey ?: "")
                    .apply { e.lesson?.let { put("lesson", lessonTo(it)) } }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun lessonTo(l: Lesson) = JSONObject()
        .put("date", l.date.toString())
        .put("slots", JSONArray().apply { l.slots.forEach { put(JSONArray().put(it.start).put(it.end)) } })
        .put("subject", l.subject)
        .put("fullSubject", l.fullSubject)
        .put("kind", l.kind.name)
        .put("location", l.location ?: "")
        .put("details", l.details)
        .put("online", l.online)
        .put("elective", l.elective ?: "")

    private fun lessonFrom(o: JSONObject, group: String, id: Long): Lesson {
        val slotsArr = o.getJSONArray("slots")
        return Lesson(
            date = LocalDate.parse(o.getString("date")),
            slots = List(slotsArr.length()) { i ->
                val a = slotsArr.getJSONArray(i); TimeSlot(a.getInt(0), a.getInt(1))
            },
            subject = o.getString("subject"),
            fullSubject = o.optString("fullSubject", o.getString("subject")),
            kind = runCatching { LessonKind.valueOf(o.getString("kind")) }.getOrDefault(LessonKind.PRACTICE),
            groups = setOf(group),
            location = o.optString("location").ifEmpty { null },
            details = o.optString("details"),
            online = o.optBoolean("online"),
            specialTime = false,
            elective = o.optString("elective").ifEmpty { null },
            editId = id,
        )
    }

    private companion object {
        const val KEY = "items"
    }
}
