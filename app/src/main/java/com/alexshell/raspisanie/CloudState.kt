package com.alexshell.raspisanie

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.data.Backup
import com.alexshell.raspisanie.data.CloudApi
import com.alexshell.raspisanie.data.CloudError
import com.alexshell.raspisanie.data.CloudUser
import com.alexshell.raspisanie.data.GroupState
import com.alexshell.raspisanie.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Профиль, облачная копия и перенос данных (сервер — Google Таблица с Apps Script). */
class CloudState(private val vm: AppViewModel) {

    private val app: Application get() = vm.getApplication()
    private val p = vm.getApplication<Application>().getSharedPreferences("cloud", Context.MODE_PRIVATE)

    var url by mutableStateOf(DEFAULT_SERVER.ifEmpty { p.getString("server", null).orEmpty() })
        private set
    var token by mutableStateOf(p.getString("token", null))
        private set
    var user by mutableStateOf(p.getString("user", null)?.let { runCatching { CloudUser.from(JSONObject(it)) }.getOrNull() })
        private set
    var auto by mutableStateOf(p.getBoolean("auto", true))
        private set
    var syncAt by mutableStateOf(p.getString("syncAt", null))
        private set

    /** Что сейчас выполняется (для индикаторов на кнопках). */
    var busy by mutableStateOf<String?>(null)
        private set
    /** Группа, объявления и личные пропуски (последний ответ сервера, хранится и офлайн). */
    private var groupRaw = p.getString("groupData", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    var groupState by mutableStateOf(GroupState.EMPTY.merge(groupRaw))
        private set
    var groupLoaded by mutableStateOf(p.contains("groupData"))
        private set
    var pulling by mutableStateOf(false)
        private set
    var groupError by mutableStateOf<String?>(null)
        private set
    var newsSeen by mutableStateOf(p.getString("newsSeen", "").orEmpty())
        private set

    /** Дата облачной копии, которую предлагаем загрузить после входа. */
    var restoreAsk by mutableStateOf<String?>(null)

    val loggedIn: Boolean get() = token != null && user != null

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
        token = null
        user = null
        clearGroup()
        save()
    }

    private fun clearGroup() {
        com.alexshell.raspisanie.notify.GroupWatch.reset(app)
        groupRaw = JSONObject()
        groupState = GroupState.EMPTY
        groupLoaded = false
        p.edit().remove("groupData").apply()
    }

    fun errorText(code: String): String {
        val key = when (code) {
            "auth" -> "Сессия истекла — войдите снова"
            "bad_login" -> "Неверный юзернейм или пароль"
            "username_taken" -> "Этот юзернейм уже занят"
            "bad_username" -> "Юзернейм: 3–24 символа — латинские буквы, цифры, «_» и «.»"
            "bad_fullname" -> "Введите полное имя: имя и фамилию"
            "password_short" -> "Пароль — минимум 4 символа"
            "bad_password" -> "Неверный пароль"
            "too_big" -> "Слишком большая копия"
            "network" -> "Нет связи с сервером"
            "no_server" -> "Сначала укажите адрес сервера"
            "no_group" -> "Вы пока не состоите в группе"
            "already_in_group" -> "Вы уже состоите в группе"
            "bad_name" -> "Введите название группы"
            "user_not_found" -> "Пользователь с таким юзернеймом не найден"
            "already_member" -> "Он уже в вашей группе"
            "in_other_group" -> "Этот пользователь уже состоит в другой группе"
            "forbidden" -> "Недостаточно прав"
            "not_found" -> "Запись не найдена"
            "creator_cannot_leave" -> "Создатель не может выйти — можно только удалить группу"
            "empty" -> "Введите текст"
            "bad_date" -> "Неверная дата"
            "bad_time" -> "Неверное время"
            "bad_subject" -> "Укажите предмет"
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
            try {
                block()
            } catch (e: CloudError) {
                vm.message = errorText(e.code)
            } catch (e: Exception) {
                vm.message = tr(e.message ?: e.toString())
            } finally {
                busy = null
            }
        }
    }

    // ---------- Проверки полей (те же правила, что на сервере) ----------

    private fun normUsername(s: String) = s.trim().removePrefix("@").lowercase()
    private fun normName(s: String) = s.trim().replace(Regex("""\s+"""), " ")
    private fun validUsername(u: String) = Regex("""^[a-z0-9_.]{3,24}$""").matches(u)
    private fun validName(n: String) = n.split(' ').count { it.isNotBlank() } >= 2

    // ---------- Сервер и вход ----------

    fun setServer(newUrl: String) {
        val u = newUrl.trim()
        if (!u.startsWith("https://")) { vm.message = tr("Введите адрес, начинающийся с https://"); return }
        url = u
        save()
        run("ping") { api("ping"); vm.message = tr("Сервер на связи ✓") }
    }

    fun register(fullName: String, username: String, password: String, avatar: String) {
        val name = normName(fullName)
        val uname = normUsername(username)
        when {
            !validName(name) -> { vm.message = errorText("bad_fullname"); return }
            !validUsername(uname) -> { vm.message = errorText("bad_username"); return }
            password.length < 4 -> { vm.message = errorText("password_short"); return }
        }
        run("auth") {
            val r = api(
                "register",
                JSONObject().put("username", uname).put("fullName", name).put("password", password)
                    .put("group", vm.group.orEmpty()).put("avatar", avatar),
            )
            signedIn(r)
            upload(silent = true)
        }
    }

    fun login(username: String, password: String) {
        val uname = normUsername(username)
        if (uname.isEmpty()) { vm.message = tr("Введите юзернейм"); return }
        run("auth") {
            val r = api("login", JSONObject().put("username", uname).put("password", password))
            signedIn(r)
            val info = runCatching { api("backupInfo") }.getOrNull()
            val updated = info?.optString("updated")?.takeIf { it.isNotEmpty() && it != "null" }
            if (updated != null) restoreAsk = updated else upload(silent = true)
        }
    }

    private fun signedIn(r: JSONObject) {
        token = r.getString("token")
        user = CloudUser.from(r.getJSONObject("user"))
        clearGroup()
        save()
        com.alexshell.raspisanie.notify.GroupWatch.schedule(app)
        pullGroup(quiet = true)
        vm.message = tr("Добро пожаловать, {0}!", user!!.fullName.substringBefore(' '))
    }

    /** Вход на новом устройстве с экрана приветствия: всё, включая файл расписания, — из облака. */
    fun loginAndRestore(serverUrl: String, username: String, password: String) {
        val u = serverUrl.trim()
        if (!u.startsWith("https://")) { vm.message = tr("Введите адрес, начинающийся с https://"); return }
        val uname = normUsername(username)
        if (uname.isEmpty()) { vm.message = tr("Введите юзернейм"); return }
        url = u
        save()
        run("auth") {
            signedIn(api("login", JSONObject().put("username", uname).put("password", password)))
            val b = api("backupLoad")
            val raw = b.optString("data").takeIf { it.isNotEmpty() && it != "null" }
            if (raw != null) {
                withContext(Dispatchers.IO) { Backup.import(app, JSONObject(raw)) }
                syncAt = java.time.Instant.now().toString()
                save()
            }
            vm.reloadAll(user?.group?.takeIf { it.isNotBlank() })
            vm.message = if (raw != null) tr("Данные загружены из облака") else tr("В облаке нет файла расписания — загрузите его")
        }
    }

    fun updateProfile(fullName: String, username: String, avatar: String, oldPassword: String, newPassword: String, onDone: () -> Unit) {
        val name = normName(fullName)
        val uname = normUsername(username)
        when {
            !validName(name) -> { vm.message = errorText("bad_fullname"); return }
            !validUsername(uname) -> { vm.message = errorText("bad_username"); return }
            newPassword.isNotEmpty() && newPassword.length < 4 -> { vm.message = errorText("password_short"); return }
        }
        run("profile") {
            val params = JSONObject().put("fullName", name).put("username", uname).put("avatar", avatar).put("group", vm.group.orEmpty())
            if (newPassword.isNotEmpty()) params.put("password", newPassword).put("oldPassword", oldPassword)
            val r = api("updateProfile", params)
            user = CloudUser.from(r.getJSONObject("user"))
            save()
            vm.message = tr("Профиль сохранён")
            onDone()
        }
    }

    fun logout() {
        val t = token
        vm.viewModelScope.launch { runCatching { if (t != null) api("logout") } }
        clearSession()
    }

    fun deleteAccount(password: String) = run("auth") {
        api("deleteAccount", JSONObject().put("password", password))
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
        api("backupSave", JSONObject().put("data", json).put("group", vm.group.orEmpty()))
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

    // ---------- Группа, объявления, пропуски ----------

    private fun applyGroup(r: JSONObject) {
        for (k in listOf("group", "members", "news", "absences", "homework", "duties")) if (r.has(k)) groupRaw.put(k, r.get(k))
        groupState = GroupState.EMPTY.merge(groupRaw)
        groupLoaded = true
        groupError = null
        r.optJSONObject("user")?.let { user = CloudUser.from(it); save() }
        p.edit().putString("groupData", groupRaw.toString()).apply()
        // ДЗ группы — сразу в личные ДЗ у каждого участника.
        if (r.has("homework")) vm.importGroupHomework(if (groupState.group != null) groupState.homework else emptyList())
        // Что видно в приложении — не присылаем уведомлением.
        com.alexshell.raspisanie.notify.GroupWatch.markSeen(
            app, groupState.group?.id.orEmpty(),
            (groupState.news.map { it.created } + groupState.homework.map { it.created }).maxOrNull(),
        )
    }

    fun pullGroup(quiet: Boolean = false) {
        if (!loggedIn || pulling) return
        vm.viewModelScope.launch {
            pulling = true
            try {
                applyGroup(api("sync"))
            } catch (e: CloudError) {
                groupError = errorText(e.code)
                if (!quiet) vm.message = groupError
            } catch (e: Exception) {
                groupError = e.message
            } finally {
                pulling = false
            }
        }
    }

    private fun groupAct(action: String, params: JSONObject = JSONObject(), ok: String? = null, then: () -> Unit = {}) = run("g") {
        applyGroup(api(action, params))
        ok?.let { vm.message = tr(it) }
        then()
    }

    val myId: String? get() = user?.id
    val canManage: Boolean get() = groupState.group?.let { it.creatorId == myId || it.starostaId == myId } == true
    val isCreator: Boolean get() = groupState.group?.creatorId == myId

    fun unseenNews(): Int {
        if (!loggedIn) return 0
        return groupState.news.count { it.authorId != myId && it.created > newsSeen }
    }

    fun markNewsSeen() {
        val last = groupState.news.maxOfOrNull { it.created } ?: return
        if (last > newsSeen) {
            newsSeen = last
            p.edit().putString("newsSeen", last).apply()
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) { vm.message = errorText("bad_name"); return }
        groupAct("groupCreate", JSONObject().put("name", name.trim()), "Группа создана")
    }

    fun renameGroup(name: String) {
        if (name.isBlank()) { vm.message = errorText("bad_name"); return }
        groupAct("groupRename", JSONObject().put("name", name.trim()))
    }

    fun leaveGroup() = groupAct("groupLeave", ok = "Вы вышли из группы")
    fun deleteGroup() = groupAct("groupDelete", ok = "Группа удалена")

    fun addMember(username: String, onDone: () -> Unit) {
        val u = normUsername(username)
        if (u.isEmpty()) { vm.message = tr("Введите юзернейм"); return }
        groupAct("memberAdd", JSONObject().put("username", u), "Одногруппник добавлен", onDone)
    }

    fun removeMember(userId: String, onDone: () -> Unit) = groupAct("memberRemove", JSONObject().put("userId", userId), then = onDone)
    fun setRole(userId: String, role: String, onDone: () -> Unit) =
        groupAct("memberRole", JSONObject().put("userId", userId).put("role", role.trim()), "Роль сохранена", onDone)
    fun setStarosta(userId: String, onDone: () -> Unit) =
        groupAct("setStarosta", JSONObject().put("userId", userId), if (userId.isEmpty()) "Староста снят" else "Староста назначен", onDone)

    fun addNews(text: String, onDone: () -> Unit) {
        if (text.isBlank()) { vm.message = errorText("empty"); return }
        groupAct("newsAdd", JSONObject().put("text", text.trim()), "Объявление опубликовано", onDone)
    }

    fun deleteNews(id: String) = groupAct("newsDelete", JSONObject().put("id", id))

    val inGroup: Boolean get() = loggedIn && groupState.group != null

    /** ДЗ для всей группы: появится в личных ДЗ у всех, включая автора. */
    fun addGroupHomework(l: com.alexshell.raspisanie.data.Lesson, text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) { vm.message = errorText("empty"); return }
        groupAct(
            "groupHwAdd",
            JSONObject().put("subject", l.subject).put("date", l.date.toString()).put("start", l.start).put("text", text.trim()),
            "ДЗ отправлено всей группе", onDone,
        )
    }

    fun deleteGroupHomework(id: String) = groupAct("groupHwDelete", JSONObject().put("id", id))

    /** Дежурный на эту пару, если назначен. */
    fun dutyFor(l: com.alexshell.raspisanie.data.Lesson): com.alexshell.raspisanie.data.Duty? =
        if (!inGroup) null else groupState.duties.firstOrNull { it.date == l.date.toString() && it.start == l.start }

    /** Назначить дежурного на пару ([userId] пустой — снять). */
    fun setDuty(l: com.alexshell.raspisanie.data.Lesson, userId: String) = groupAct(
        "dutySet",
        JSONObject().put("date", l.date.toString()).put("start", l.start).put("subject", l.subject).put("userId", userId),
        if (userId.isEmpty()) "Дежурный снят" else "Дежурный назначен",
    )

    fun saveAbsence(a: com.alexshell.raspisanie.data.Absence, onDone: () -> Unit) {
        if (a.subject.isBlank()) { vm.message = errorText("bad_subject"); return }
        groupAct(
            "absSave",
            JSONObject().put("id", a.id).put("date", a.date).put("subject", a.subject.trim()).put("note", a.note.trim())
                .put("makeupDate", a.makeupDate).put("makeupTime", a.makeupTime).put("makeupPlace", a.makeupPlace.trim())
                .put("done", a.done),
            if (a.id.isEmpty()) "Пропуск добавлен" else "Сохранено", onDone,
        )
    }

    fun deleteAbsence(id: String, onDone: () -> Unit) = groupAct("absDelete", JSONObject().put("id", id), then = onDone)

    companion object {
        /** Ссылка на веб-приложение вашей Google Таблицы (…/exec) — тогда вводить адрес в приложении не нужно. */
        const val DEFAULT_SERVER = "https://script.google.com/macros/s/AKfycbwnRxjo9g6W1shjOAQX9FAPcG81kS18Y0xEwoKCKrj6KFQ59noCPPY5CwahH1x45vroXQ/exec"
    }
}
