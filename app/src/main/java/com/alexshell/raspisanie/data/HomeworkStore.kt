package com.alexshell.raspisanie.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Homework(
    val id: Long,
    val group: String,
    /** Ключ занятия, к которому нужно сделать задание (см. [Lesson.key]). */
    val lessonKey: String,
    val subject: String,
    val dueDate: LocalDate,
    val dueStart: Int,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Хранит домашние задания в SharedPreferences в виде JSON. */
class HomeworkStore(context: Context) {
    private val prefs = context.getSharedPreferences("homework", Context.MODE_PRIVATE)

    fun load(): List<Homework> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Homework(
                    id = o.getLong("id"),
                    group = o.getString("group"),
                    lessonKey = o.getString("lessonKey"),
                    subject = o.getString("subject"),
                    dueDate = LocalDate.parse(o.getString("dueDate")),
                    dueStart = o.getInt("dueStart"),
                    text = o.getString("text"),
                    done = o.optBoolean("done"),
                    createdAt = o.optLong("createdAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<Homework>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("group", h.group)
                    .put("lessonKey", h.lessonKey)
                    .put("subject", h.subject)
                    .put("dueDate", h.dueDate.toString())
                    .put("dueStart", h.dueStart)
                    .put("text", h.text)
                    .put("done", h.done)
                    .put("createdAt", h.createdAt)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
