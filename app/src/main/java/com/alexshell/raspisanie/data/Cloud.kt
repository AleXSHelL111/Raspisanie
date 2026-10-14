package com.alexshell.raspisanie.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

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
    val group: String,
    val name: String,
    val avatar: String,
    val contact: String,
    val role: String,
) {
    val isLead: Boolean get() = role == "starosta" || role == "deputy"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("group", group).put("name", name)
        .put("avatar", avatar).put("contact", contact).put("role", role)

    companion object {
        fun from(o: JSONObject) = CloudUser(
            o.optString("id"), o.optString("group"), o.optString("name"),
            o.optString("avatar"), o.optString("contact"), o.optString("role", "member"),
        )
    }
}

data class GroupHw(
    val id: String, val subject: String, val date: LocalDate, val start: Int,
    val text: String, val authorId: String, val author: String,
)

data class GroupNews(
    val id: String, val kind: String, val title: String, val text: String,
    val authorId: String, val author: String, val created: String,
)

data class AttRecord(
    val id: String, val userId: String, val name: String, val date: LocalDate,
    val subject: String, val status: String, val note: String,
)

/** Всё для экрана «Моя группа» (ответ action=group). */
data class GroupData(
    val user: CloudUser,
    val members: List<CloudUser>,
    val homework: List<GroupHw>,
    val news: List<GroupNews>,
    val attendance: List<AttRecord>,
) {
    companion object {
        private fun date(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrDefault(LocalDate.MIN)
        private inline fun <T> JSONArray?.list(f: (JSONObject) -> T): List<T> =
            if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

        fun from(o: JSONObject) = GroupData(
            user = CloudUser.from(o.getJSONObject("user")),
            members = o.optJSONArray("members").list(CloudUser::from),
            homework = o.optJSONArray("homework").list {
                GroupHw(it.optString("id"), it.optString("subject"), date(it.optString("date")), it.optInt("start"),
                    it.optString("text"), it.optString("authorId"), it.optString("author"))
            },
            news = o.optJSONArray("news").list {
                GroupNews(it.optString("id"), it.optString("kind", "news"), it.optString("title"), it.optString("text"),
                    it.optString("authorId"), it.optString("author"), it.optString("created"))
            },
            attendance = o.optJSONArray("attendance").list {
                AttRecord(it.optString("id"), it.optString("userId"), it.optString("name"), date(it.optString("date")),
                    it.optString("subject"), it.optString("status", "absent"), it.optString("note"))
            },
        )
    }
}
