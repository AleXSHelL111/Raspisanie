package com.alexshell.raspisanie.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ошибка сервера: [code] — код из ответа Apps Script (auth, bad_login, …). */
class CloudError(val code: String) : Exception(code)

/** Клиент веб-приложения Google Apps Script (server/Code.gs). Вызывать только из фонового потока. */
object CloudApi {

    fun call(url: String, body: JSONObject): JSONObject {
        val text = runCatching { request(url, body.toString().toByteArray()) }.getOrElse { throw CloudError("network") }
        val json = runCatching { JSONObject(text) }.getOrElse { throw CloudError("network") }
        if (!json.optBoolean("ok")) throw CloudError(json.optString("error", "server"))
        return json
    }

    /**
     * Apps Script отвечает на POST редиректом 302 на googleusercontent.com,
     * поэтому редиректы проходим вручную (следующий запрос — GET).
     */
    private fun request(start: String, body: ByteArray): String {
        var url = URL(start)
        var data: ByteArray? = body
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 40_000
            if (data != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                c.outputStream.use { it.write(data) }
            } else {
                c.requestMethod = "GET"
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: error("redirect")
                c.disconnect()
                url = URL(url, loc)
                data = null
                return@repeat
            }
            val stream = if (code >= 400) c.errorStream else c.inputStream
            return stream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        }
        error("too many redirects")
    }
}

data class CloudUser(
    val id: String,
    val username: String,
    val fullName: String,
    val group: String,
    val avatar: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("username", username).put("fullName", fullName)
        .put("group", group).put("avatar", avatar)

    companion object {
        fun from(o: JSONObject) = CloudUser(
            o.optString("id"), o.optString("username"), o.optString("fullName"),
            o.optString("group"), o.optString("avatar"),
        )
    }
}

/** Группа: создатель и староста — по id участников. */
data class GroupInfo(val id: String, val name: String, val creatorId: String, val starostaId: String)

data class GroupMember(
    val id: String, val username: String, val fullName: String, val avatar: String,
    val role: String, val creator: Boolean, val starosta: Boolean,
)

data class GroupNews(
    val id: String, val text: String, val created: String,
    val authorId: String, val author: String, val authorAvatar: String,
)

/** Личный пропуск и отработка. Даты — «ГГГГ-ММ-ДД», время — «ЧЧ:ММ». */
data class Absence(
    val id: String, val date: String, val subject: String, val note: String,
    val makeupDate: String, val makeupTime: String, val makeupPlace: String, val done: Boolean,
)

/** ДЗ для всей группы. */
data class GroupHomework(
    val id: String, val subject: String, val date: String, val start: Int, val text: String,
    val authorId: String, val author: String, val created: String,
)

/** Дежурный на пару: дата «ГГГГ-ММ-ДД» и начало пары в минутах. */
data class Duty(val date: String, val start: Int, val subject: String, val userId: String, val fullName: String)

/** Ответ action=sync (и частичные ответы действий группы). */
data class GroupState(
    val group: GroupInfo?,
    val members: List<GroupMember>,
    val news: List<GroupNews>,
    val absences: List<Absence>,
    val homework: List<GroupHomework> = emptyList(),
    val duties: List<Duty> = emptyList(),
) {
    /** Новый ответ поверх старого: поля, которых нет в ответе, сохраняются. */
    fun merge(o: org.json.JSONObject): GroupState = GroupState(
        group = if (o.has("group")) o.optJSONObject("group")?.let {
            GroupInfo(it.optString("id"), it.optString("name"), it.optString("creatorId"), it.optString("starostaId"))
        } else group,
        members = o.optJSONArray("members")?.let { a ->
            List(a.length()) { i ->
                val m = a.getJSONObject(i)
                GroupMember(
                    m.optString("id"), m.optString("username"), m.optString("fullName"), m.optString("avatar"),
                    m.optString("role"), m.optBoolean("creator"), m.optBoolean("starosta"),
                )
            }
        } ?: members,
        news = o.optJSONArray("news")?.let { a ->
            List(a.length()) { i ->
                val n = a.getJSONObject(i)
                GroupNews(n.optString("id"), n.optString("text"), n.optString("created"), n.optString("authorId"), n.optString("author"), n.optString("authorAvatar"))
            }
        } ?: news,
        absences = o.optJSONArray("absences")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                Absence(
                    x.optString("id"), x.optString("date"), x.optString("subject"), x.optString("note"),
                    x.optString("makeupDate"), x.optString("makeupTime"), x.optString("makeupPlace"), x.optBoolean("done"),
                )
            }
        } ?: absences,
        homework = o.optJSONArray("homework")?.let { a ->
            List(a.length()) { i ->
                val h = a.getJSONObject(i)
                GroupHomework(
                    h.optString("id"), h.optString("subject"), h.optString("date"), h.optInt("start"), h.optString("text"),
                    h.optString("authorId"), h.optString("author"), h.optString("created"),
                )
            }
        } ?: homework,
        duties = o.optJSONArray("duties")?.let { a ->
            List(a.length()) { i ->
                val d = a.getJSONObject(i)
                Duty(d.optString("date"), d.optInt("start"), d.optString("subject"), d.optString("userId"), d.optString("fullName"))
            }
        } ?: duties,
    )

    companion object {
        val EMPTY = GroupState(null, emptyList(), emptyList(), emptyList())
    }
}
