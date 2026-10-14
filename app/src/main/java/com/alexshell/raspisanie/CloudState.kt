package com.alexshell.raspisanie

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.data.AttRecord
import com.alexshell.raspisanie.data.Backup
import com.alexshell.raspisanie.data.CloudApi
import com.alexshell.raspisanie.data.CloudError
import com.alexshell.raspisanie.data.CloudUser
import com.alexshell.raspisanie.data.GroupData
import com.alexshell.raspisanie.data.GroupHw
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.ui.tr
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.weekdayShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Профиль, облачная копия и раздел «Моя группа» (сервер — Google-таблица с Apps Script). */
class CloudState(private val vm: AppViewModel) {

    private val app: Application get() = vm.getApplication()
    private val p = vm.getApplication<Application>().getSharedPreferences("cloud", Context.MODE_PRIVATE)

    var url by mutableStateOf(p.getString("server", null).orEmpty().ifEmpty { DEFAULT_SERVER })
        private set
    var token by mutableStateOf(p.getString("token", null))
        private set
    var user by mutableStateOf(p.getString("user", null)?.let { runCatching { CloudUser.from(JSONObject(it)) }.getOrNull() })
        private set
    var data by mutableStateOf(p.getString("groupData", null)?.let { runCatching { GroupData.from(JSONObject(it)) }.getOrNull() })
        private set
    private var dataAt = p.getLong("dataAt", 0)
    var auto by mutableStateOf(p.getBoolean("auto", true))
        private set
    var syncAt by mutableStateOf(p.getString("syncAt", null))
        private set
    var newsSeen by mutableStateOf(p.getString("newsSeen", "").orEmpty())
        private set
    var importedHw by mutableStateOf(p.getStringSet("importedHw", emptySet()).orEmpty().toSet())
        private set

    /** Что сейчас выполняется (для индикаторов на кнопках). */
    var busy by mutableStateOf<String?>(null)
        private set
    var pulling by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Дата облачной копии, которую предлагаем загрузить после входа. */
    var restoreAsk by mutableStateOf<String?>(null)

    val loggedIn: Boolean get() = token != null && user != null
    val isLead: Boolean get() = user?.isLead == true

    fun unseenNews(): Int {
        val d = data ?: return 0
        val me = user?.id ?: return 0
        return d.news.count { it.authorId != me && it.created > newsSeen }
    }

    fun markNewsSeen() {
        val last = data?.news?.maxOfOrNull { it.created } ?: return
        if (last > newsSeen) {
            newsSeen = last
            p.edit().putString("newsSeen", last).apply()
        }
    }

    private fun save() {
        p.edit()
            .putString("server", url)
            .putString("token", token)
            .putString("user", user?.toJson()?.toString())
            .putBoolean("auto", auto)
            .putString("syncAt", syncAt)
            .apply()
    }

    private fun clearSession() {
        token = null; user = null; data = null
        p.edit().remove("groupData").apply()
        save()
    }

    fun errorText(code: String): String {
        val key = when (code) {
            "auth" -> "Сессия истекла — войдите снова"
            "bad_login" -> "Неверное имя или пароль"
            "name_taken" -> "Это имя в группе уже занято"
            "password_short" -> "Пароль — минимум 4 символа"
            "forbidden" -> "Недостаточно прав"
            "not_found" -> "Запись не найдена"
            "name" -> "Введите имя"
            "text" -> "Введите текст"
            "subject" -> "Выберите предмет"
            "date" -> "Неверная дата"
            "group" -> "Сначала выберите группу"
            "too_big" -> "Слишком большая копия"
            "network" -> "Нет связи с сервером"
            "no_server" -> "Сначала укажите адрес сервера"
            else -> return tr("Ошибка сервера: {0}", code)
        }
        return tr(key)
    }

    private suspend fun api(action: String, params: JSONObject = JSONObject()): JSONObject {
        if (url.isBlank()) throw CloudError("no_server")
        params.put("action", action).put("token", token ?: JSONObject.NULL)
        return try {
            withContext(Dispatchers.IO) { CloudApi.call(url, params) }
        } catch (e: CloudError) {
            if (e.code == "auth" && token != null) clearSession()
            throw e
        }
    }

    /** Запрос с индикатором и сообщением об ошибке. */
    private fun run(label: String, block: suspend () -> Unit) {
        vm.viewModelScope.launch {
            busy = label
            error = null
            try {
                block()
            } catch (e: CloudError) {
                error = errorText(e.code); vm.message = error
            } catch (e: Exception) {
                error = e.message ?: e.toString(); vm.message = error
            } finally {
                busy = null
            }
        }
    }

    // ---------- Сервер и вход ----------

    fun setServer(newUrl: String) {
        val u = newUrl.trim()
        if (!u.startsWith("https://")) { vm.message = tr("Введите адрес, начинающийся с https://"); return }
        url = u
        save()
        run("ping") { api("ping"); vm.message = tr("Сервер на связи ✓") }
    }

    fun login(group: String, name: String, password: String) = auth("login", JSONObject().put("group", group).put("name", name.trim()).put("password", password))

    fun register(group: String, name: String, password: String, contact: String, avatar: String) = auth(
        "register",
        JSONObject().put("group", group).put("name", name.trim()).put("password", password)
            .put("contact", contact.trim()).put("avatar", avatar),
    )

    private fun auth(action: String, params: JSONObject) {
        if (params.optString("name").isBlank()) { vm.message = errorText("name"); return }
        if (params.optString("password").length < 4) { vm.message = errorText("password_short"); return }
        run("auth") {
            val r = api(action, params)
            token = r.getString("token")
            user = CloudUser.from(r.getJSONObject("user"))
            data = null
            save()
            vm.message = tr("Добро пожаловать, {0}!", user!!.name)
            val info = runCatching { api("backupInfo") }.getOrNull()
            val updated = info?.optString("updated")?.takeIf { it.isNotEmpty() && it != "null" }
            if (updated != null) restoreAsk = updated else upload(silent = true)
            pullGroup(quiet = true)
        }
    }

    /** Вход на новом устройстве с экрана приветствия: всё, включая файл расписания, — из облака. */
    fun loginAndRestore(serverUrl: String, group: String, name: String, password: String) {
        val u = serverUrl.trim()
        if (!u.startsWith("https://")) { vm.message = tr("Введите адрес, начинающийся с https://"); return }
        if (group.isBlank()) { vm.message = errorText("group"); return }
        url = u
        save()
        run("auth") {
            val r = api("login", JSONObject().put("group", group.trim()).put("name", name.trim()).put("password", password))
            token = r.getString("token")
            user = CloudUser.from(r.getJSONObject("user"))
            save()
            val b = api("backupLoad")
            val raw = b.optString("data").takeIf { it.isNotEmpty() && it != "null" }
            if (raw != null) {
                withContext(Dispatchers.IO) { Backup.import(app, JSONObject(raw)) }
                syncAt = java.time.Instant.now().toString(); save()
            }
            vm.reloadAll(group.trim())
            vm.message = if (raw != null) tr("Данные загружены из облака") else tr("В облаке нет файла расписания — загрузите его")
            pullGroup(quiet = true)
        }
    }

    fun updateProfile(name: String, contact: String, avatar: String, password: String, onDone: () -> Unit) {
        if (password.isNotEmpty() && password.length < 4) { vm.message = errorText("password_short"); return }
        run("profile") {
            val params = JSONObject().put("name", name.trim()).put("contact", contact.trim()).put("avatar", avatar)
            if (password.isNotEmpty()) params.put("password", password)
            val r = api("updateProfile", params)
            user = CloudUser.from(r.getJSONObject("user"))
            save()
            vm.message = tr("Профиль сохранён")
            onDone()
            pullGroup(quiet = true)
        }
    }

    fun logout() {
        val t = token
        vm.viewModelScope.launch { runCatching { if (t != null) api("logout") } }
        clearSession()
    }

    fun deleteAccount() = run("auth") {
        api("deleteAccount")
        clearSession()
        vm.message = tr("Профиль удалён")
    }

    // ---------- Облачная копия ----------

    fun toggleAuto() {
        auto = !auto
        save()
        if (auto) changed()
    }

    private var autoJob: Job? = null

    /** Автосохранение через несколько секунд после изменений. */
    fun changed() {
        if (!loggedIn || !auto || url.isBlank()) return
        autoJob?.cancel()
        autoJob = vm.viewModelScope.launch {
            delay(5000)
            runCatching { upload(silent = true) }
        }
    }

    private suspend fun upload(silent: Boolean) {
        val json = withContext(Dispatchers.IO) { Backup.export(app).toString() }
        api("backupSave", JSONObject().put("data", json))
        syncAt = java.time.Instant.now().toString()
        save()
        if (!silent) vm.message = tr("Сохранено в облако")
    }

    fun uploadNow() = run("upload") { upload(silent = false) }

    fun download() = run("download") {
        val r = api("backupLoad")
        val raw = r.optString("data").takeIf { it.isNotEmpty() && it != "null" }
        if (raw == null) { vm.message = tr("В облаке пока нет копии"); return@run }
        withContext(Dispatchers.IO) { Backup.import(app, JSONObject(raw)) }
        vm.reloadAll(null)
        syncAt = java.time.Instant.now().toString()
        save()
        vm.message = tr("Данные загружены из облака")
    }

    fun answerRestore(load: Boolean) {
        restoreAsk = null
        if (load) download() else run("upload") { upload(silent = true) }
    }

    // ---------- Группа ----------

    fun pullIfStale() {
        if (loggedIn && System.currentTimeMillis() - dataAt > 60_000) pullGroup(quiet = true)
    }

    fun pullGroup(quiet: Boolean = false) {
        if (!loggedIn || pulling) return
        vm.viewModelScope.launch {
            pulling = true
            try {
                val r = api("group")
                data = GroupData.from(r)
                user = data!!.user
                dataAt = System.currentTimeMillis()
                p.edit().putString("groupData", r.toString()).putLong("dataAt", dataAt).apply()
                save()
                error = null
            } catch (e: CloudError) {
                error = errorText(e.code)
                if (!quiet) vm.message = error
            } catch (e: Exception) {
                error = e.message
            } finally {
                pulling = false
            }
        }
    }

    private fun act(label: String, action: String, params: JSONObject, ok: String? = null, then: () -> Unit = {}) = run(label) {
        api(action, params)
        ok?.let { vm.message = tr(it) }
        then()
        pullGroup(quiet = true)
    }

    fun addNews(kind: String, title: String, text: String, onDone: () -> Unit) {
        if (text.isBlank()) { vm.message = errorText("text"); return }
        act("news", "newsAdd", JSONObject().put("kind", kind).put("title", title.trim()).put("text", text.trim()), "Объявление опубликовано", onDone)
    }

    fun deleteNews(id: String) = act("del", "newsDelete", JSONObject().put("id", id))

    fun shareHomework(l: Lesson, text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) { vm.message = errorText("text"); return }
        act(
            "ghw", "hwAdd",
            JSONObject().put("subject", l.subject).put("date", l.date.toString()).put("start", l.start).put("text", text.trim()),
            "ДЗ отправлено группе", onDone,
        )
    }

    fun deleteGroupHw(id: String) = act("del", "hwDelete", JSONObject().put("id", id))

    /** Добавить общее ДЗ себе — к той же паре в своём расписании. */
    fun takeHomework(h: GroupHw) {
        val l = vm.lessons.firstOrNull { it.subject == h.subject && it.date == h.date && it.start == h.start }
            ?: vm.lessons.firstOrNull { it.subject == h.subject && it.date == h.date }
            ?: vm.nextBySubject(h.subject)
        if (l == null) { vm.message = tr("Этой пары нет в вашем расписании"); return }
        vm.addHomework(l, h.text)
        importedHw = importedHw + h.id
        p.edit().putStringSet("importedHw", importedHw).apply()
        vm.message = tr("ДЗ добавлено на {0}", "${l.date.weekdayShort()}, ${l.date.dayMonth()}")
    }

    fun setRole(userId: String, role: String, onDone: () -> Unit) =
        act("role", "setRole", JSONObject().put("userId", userId).put("role", role), then = onDone)

    fun removeMember(userId: String, onDone: () -> Unit) =
        act("role", "removeMember", JSONObject().put("userId", userId), then = onDone)

    fun addAttendance(date: LocalDate, subject: String, status: String, note: String, onDone: () -> Unit) {
        if (subject.isBlank()) { vm.message = errorText("subject"); return }
        act(
            "att", "attAdd",
            JSONObject().put("date", date.toString()).put("subject", subject).put("status", status).put("note", note.trim()),
            "Отмечено", onDone,
        )
    }

    fun setAttendance(r: AttRecord, status: String) = act("att", "attUpdate", JSONObject().put("id", r.id).put("status", status))

    fun deleteAttendance(r: AttRecord) = act("att", "attDelete", JSONObject().put("id", r.id))

    companion object {
        /** Можно вписать адрес своего сервера (…/exec) — тогда вводить его в приложении не нужно. */
        const val DEFAULT_SERVER = ""
    }
}
