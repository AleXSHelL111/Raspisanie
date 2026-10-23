package com.alexshell.raspisanie.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия в общем формате с веб-версией:
 * { app:"kgmu", v:1, settings:{…}, hw:[…], edits:[…], file:"base64 xlsx" }.
 */
object Backup {

    fun export(context: Context, withFile: Boolean = true): JSONObject {
        val p = Repository.prefs(context)
        val n = com.alexshell.raspisanie.notify.Notifier.settings(context)
        val settings = JSONObject()
            .put("group", p.getString("group", null) ?: JSONObject.NULL)
            .put("stream", p.getInt("stream", 0).takeIf { it > 0 } ?: JSONObject.NULL)
            .put("electives", JSONArray(p.getStringSet("electives", emptySet()).orEmpty().toList()))
            .put("theme", p.getString("theme", "neon"))
            .put("mode", p.getString("theme_mode", "DARK"))
            .put("lang", com.alexshell.raspisanie.ui.I18n.lang)
            .put("aiRules", p.getString("ai_rules", "").orEmpty())
            .put(
                "notif", JSONObject()
                    .put("before", n.beforeLesson).put("lead", n.leadMinutes)
                    .put("evening", n.evening).put("eveningAt", n.eveningAt).put("homework", n.homework)
            )
        val hw = context.getSharedPreferences("homework", Context.MODE_PRIVATE).getString("items", null) ?: "[]"
        val edits = context.getSharedPreferences("edits", Context.MODE_PRIVATE).getString("items", null) ?: "[]"
        val file = Repository.importedFile(context)
        return JSONObject()
            .put("app", "kgmu").put("v", 1)
            .put("created", java.time.Instant.now().toString())
            .put("settings", settings)
            .put("hw", JSONArray(hw))
            .put("edits", JSONArray(edits))
            .put("file", if (withFile && file.exists()) Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) else JSONObject.NULL)
    }

    /** Записывает копию на устройство. После вызова нужно перечитать состояние приложения. */
    fun import(context: Context, d: JSONObject) {
        require(d.optString("app") == "kgmu") { "Это не файл копии КГМУ расписания" }
        // Файл расписания проверяем до того, как что-то перезаписать.
        val fileBytes = d.optString("file").takeIf { it.isNotEmpty() && it != "null" }?.let { Base64.decode(it, Base64.DEFAULT) }
        fileBytes?.let { ScheduleParser.parse(it.inputStream()) }

        val st = d.optJSONObject("settings") ?: JSONObject()
        val e = Repository.prefs(context).edit()
        st.optString("group").takeIf { it.isNotEmpty() && it != "null" }?.let { e.putString("group", it) }
        st.optInt("stream", 0).let { if (it > 0) e.putInt("stream", it) else e.remove("stream") }
        st.optJSONArray("electives")?.let { a -> e.putStringSet("electives", List(a.length()) { a.getString(it) }.toSet()) }
        st.optString("theme").takeIf { it.isNotEmpty() }?.let { e.putString("theme", it) }
        st.optString("mode").takeIf { it.isNotEmpty() }?.let { e.putString("theme_mode", it) }
        if (st.has("aiRules")) e.putString("ai_rules", st.optString("aiRules"))
        st.optJSONObject("notif")?.let { n ->
            e.putBoolean("notif_before", n.optBoolean("before", true))
                .putInt("notif_lead", n.optInt("lead", 15))
                .putBoolean("notif_evening", n.optBoolean("evening", true))
                .putInt("notif_evening_at", n.optInt("eveningAt", 20 * 60))
                .putBoolean("notif_hw", n.optBoolean("homework", true))
        }
        e.apply()
        st.optString("lang").takeIf { l -> com.alexshell.raspisanie.ui.I18n.languages.any { it.code == l } }
            ?.let { com.alexshell.raspisanie.ui.I18n.set(context, it) }

        // ДЗ и правки — в том же виде, что хранят HomeworkStore / EditStore.
        val hw = d.optJSONArray("hw") ?: JSONArray()
        context.getSharedPreferences("homework", Context.MODE_PRIVATE).edit().putString("items", hw.toString()).apply()
        val edits = d.optJSONArray("edits") ?: JSONArray()
        for (i in 0 until edits.length()) {
            val o = edits.getJSONObject(i)
            if (o.isNull("origKey")) o.put("origKey", "")
            if (o.isNull("lesson")) o.remove("lesson")
            o.optJSONObject("lesson")?.let { l ->
                if (l.isNull("location")) l.put("location", "")
                if (l.isNull("elective")) l.put("elective", "")
            }
        }
        context.getSharedPreferences("edits", Context.MODE_PRIVATE).edit().putString("items", edits.toString()).apply()
        fileBytes?.let { Repository.importedFile(context).writeBytes(it) }
    }
}
