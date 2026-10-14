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
