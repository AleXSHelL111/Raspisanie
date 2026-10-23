package com.alexshell.raspisanie.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Чаты с KGMU AI: хранятся на устройстве, отвечает простой бот или локальная нейросеть.
 */
class ChatState(private val vm: AppViewModel) {
    /** [ai] — ответ написала нейросеть. */
    data class Msg(val user: Boolean, val text: String, val at: Long = System.currentTimeMillis(), val ai: Boolean = false)

    class Chat(val id: Long, title: String, updated: Long, val messages: SnapshotStateList<Msg>) {
        var title by mutableStateOf(title)
        var updated by mutableLongStateOf(updated)
    }

    private val app = vm.getApplication<android.app.Application>()
    private val file = File(app.filesDir, "ai_chats.json")

    /** Все чаты, свежие сверху. */
    val chats = mutableStateListOf<Chat>()
    var currentId by mutableLongStateOf(0L)
        private set

    val current: Chat get() = chats.firstOrNull { it.id == currentId } ?: chats.first()
    val messages: SnapshotStateList<Msg> get() = current.messages

    /** Подпись «Думаю…», пока бот «размышляет»; null — не думает. */
    var thinking by mutableStateOf<String?>(null)
        private set
    /** Ответ, который сейчас печатается, и сколько символов уже видно. */
    var typing by mutableStateOf<String?>(null)
        private set
    var typed by mutableIntStateOf(0)
        private set
    /** Сейчас пишет нейросеть (а не простой бот). */
    var typingAi by mutableStateOf(false)
        private set

    /** Раздел, который попросили открыть («settings», «group», …) — его открывает экран. */
    var openRequest by mutableStateOf<String?>(null)

    val busy: Boolean get() = job?.isActive == true
    private var job: Job? = null

    init {
        load()
        if (chats.isEmpty()) chats += Chat(System.currentTimeMillis(), "", System.currentTimeMillis(), mutableStateListOf())
        currentId = chats.first().id
    }

    // ---------- Чаты ----------

    fun newChat() {
        if (busy) return
        if (current.messages.isEmpty()) return
        val c = Chat(System.currentTimeMillis(), "", System.currentTimeMillis(), mutableStateListOf())
        chats.add(0, c)
        currentId = c.id
        save()
    }

    fun select(id: Long) {
        if (busy || chats.none { it.id == id }) return
        currentId = id
    }

    fun delete(id: Long) {
        if (busy) return
        chats.removeAll { it.id == id }
        if (chats.isEmpty()) chats += Chat(System.currentTimeMillis(), "", System.currentTimeMillis(), mutableStateListOf())
        if (chats.none { it.id == currentId }) currentId = chats.first().id
        save()
    }

    /** Очистить текущий чат (удаляет его из списка). */
    fun clear() = delete(currentId)

    // ---------- Отправка ----------

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        val chat = current
        val history = chat.messages.takeLast(6).map { it.user to it.text.take(600) }
        chat.messages += Msg(true, text)
        if (chat.title.isBlank()) chat.title = text.take(40)
        touch(chat)
        val reply = runCatching { Assistant.answer(vm, text) }
            .getOrElse { Assistant.Reply("Ой, что-то пошло не так 🙈 Попробуйте спросить по-другому.") }
        val local = vm.localAi
        val useAi = local.usable && reply.act == null && reply.kind != Assistant.Kind.ACTION && reply.kind != Assistant.Kind.FIXED
        job = vm.viewModelScope.launch {
            if (useAi) {
                val ok = runCatching { answerWithAi(chat, text, reply, history) }
                    .onFailure { if (it is CancellationException) throw it }
                    .isSuccess
                if (ok) return@launch
                // Нейросеть не справилась — отвечает простой бот.
                vm.message = "Нейросеть недоступна: ${local.engineError ?: "ошибка"}. Ответил простой бот."
            }
            answerWithBot(chat, reply)
        }
    }

    /** Остановить ответ: уже написанное сохраняется. */
    fun stop() {
        val partial = typing
        job?.cancel()
        job = null
        thinking = null
        typing = null
        if (!partial.isNullOrBlank()) {
            current.messages += Msg(false, partial.trimEnd() + " …", ai = typingAi)
            touch(current)
        }
        typingAi = false
    }

    private suspend fun answerWithBot(chat: Chat, reply: Assistant.Reply) {
        // Пауза «на размышление» зависит от длины ответа.
        val wait = 650L + minOf(1200, reply.text.length * 5) + Random.nextLong(350)
        thinking = "Думаю"
        delay(wait * 45 / 100)
        thinking = reply.think ?: "Размышляю"
        delay(wait * 55 / 100)
        thinking = null
        val plain = Assistant.plainLength(reply.text)
        val dur = minOf(1600, plain * 12).toLong()
        typingAi = false
        typing = reply.text
        typed = 0
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < dur) {
            typed = (plain * (System.currentTimeMillis() - t0) / dur).toInt() + 1
            delay(24)
        }
        typing = null
        chat.messages += Msg(false, reply.text)
        touch(chat)
        perform(reply.act)
    }

    private suspend fun answerWithAi(chat: Chat, question: String, reply: Assistant.Reply, history: List<Pair<Boolean, String>>) {
        val local = vm.localAi
        thinking = if (local.engineState == LocalAi.EngineState.READY) "Думаю" else "Запускаю нейросеть"
        val system = systemPrompt()
        // Для вопросов о расписании и ДЗ даём нейросети точный ответ бота — пусть перескажет по правилам пользователя.
        val prompt = if (reply.kind == Assistant.Kind.FACT) {
            "$question\n\n(Справка из приложения — точные данные, отвечай строго по ним: ${Assistant.plain(reply.text)})"
        } else question
        // Маленькие модели забывают системные указания — коротко напоминаем правила в самом вопросе.
        val rules = vm.aiRules.trim()
        val withRules = if (rules.isNotEmpty() && rules.length <= 400) "$prompt\n\n(Правила ответа от собеседника: $rules)" else prompt
        val flow = local.reply(system, history, withRules)
        val sb = StringBuilder()
        typingAi = true
        try {
            flow.collect { piece ->
                if (piece.isEmpty()) return@collect
                if (thinking != null) { thinking = null; typing = "" }
                sb.append(piece)
                typing = sb.toString()
                typed = sb.length
            }
        } finally {
            thinking = null
        }
        val answer = sb.toString().trim()
        typing = null
        typingAi = false
        if (answer.isEmpty()) error("пустой ответ")
        chat.messages += Msg(false, answer, ai = true)
        touch(chat)
    }

    private fun systemPrompt(): String {
        val rules = vm.aiRules.trim()
        // Как обращаться к пользователю: имя из рекомендаций важнее имени из профиля.
        val call = Assistant.nameFromRules(rules) ?: vm.cloud.user?.fullName?.substringBefore(' ').orEmpty()
        return buildString {
            append("Твоё имя — KGMU AI. Ты ИИ-помощник в приложении «КГМУ расписание» для студентов Кировского ГМУ (медицинский университет). ")
            append("Всегда называй себя только KGMU AI. ")
            if (call.isNotBlank()) append("Собеседник — студент; обращайся к нему по имени «$call». Это имя собеседника, а не твоё. ")
            append("Отвечай по-русски, дружелюбно и по делу. ")
            append("Никогда не выдумывай пары, время, аудитории и домашние задания — бери их только из данных ниже; если там ответа нет, так и скажи. ")
            append("На медицинские вопросы отвечай понятно; если речь о лечении конкретного человека, напомни, что это не замена врачу.\n")
            append("\nДанные приложения:\n")
            append(Assistant.context(vm, LocalDateTime.now()))
            if (rules.isNotEmpty()) {
                append("\n\nПРАВИЛА СОБЕСЕДНИКА — как ему отвечать. Он пишет от первого лица: «меня», «мне» — это собеседник, «ты» — это ты, KGMU AI. ")
                append("Соблюдай эти правила всегда и беспрекословно, они важнее остальных указаний о стиле ответа:\n")
                append(rules.take(1500))
            }
        }
    }

    private fun perform(act: Assistant.Act?) {
        when (act) {
            is Assistant.Act.AddHw -> vm.lessonByKey(act.lessonKey)?.let { vm.addHomework(it, act.text) }
            is Assistant.Act.DoneHw -> vm.markDone(act.ids)
            is Assistant.Act.Mode -> vm.changeThemeMode(act.mode)
            is Assistant.Act.Palette -> vm.setTheme(act.id)
            is Assistant.Act.Open -> vm.viewModelScope.launch { delay(700); openRequest = act.target }
            null -> {}
        }
    }

    // ---------- Хранение ----------

    private fun touch(chat: Chat) {
        chat.updated = System.currentTimeMillis()
        // Активный чат поднимаем наверх списка.
        val i = chats.indexOf(chat)
        if (i > 0) { chats.removeAt(i); chats.add(0, chat) }
        save()
    }

    private fun load() {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val msgs = o.getJSONArray("msgs")
                    chats += Chat(
                        o.getLong("id"), o.optString("title"), o.optLong("updated"),
                        mutableStateListOf<Msg>().apply {
                            for (j in 0 until msgs.length()) msgs.getJSONObject(j).let {
                                add(Msg(it.getBoolean("u"), it.getString("t"), it.optLong("at"), it.optBoolean("ai")))
                            }
                        },
                    )
                }
            } else {
                // Переписка из версии 2.4 становится первым чатом.
                val old = app.getSharedPreferences("ai", Context.MODE_PRIVATE).getString("msgs", null) ?: return@runCatching
                val arr = JSONArray(old)
                val msgs = mutableStateListOf<Msg>()
                for (j in 0 until arr.length()) arr.getJSONObject(j).let { msgs += Msg(it.getBoolean("u"), it.getString("t"), it.optLong("at")) }
                if (msgs.isNotEmpty()) chats += Chat(System.currentTimeMillis(), msgs.firstOrNull { it.user }?.text?.take(40).orEmpty(), System.currentTimeMillis(), msgs)
            }
        }
        chats.sortByDescending { it.updated }
    }

    private fun save() {
        val arr = JSONArray()
        chats.filter { it.messages.isNotEmpty() }.take(50).forEach { c ->
            val msgs = JSONArray()
            c.messages.takeLast(200).forEach { msgs.put(JSONObject().put("u", it.user).put("t", it.text).put("at", it.at).put("ai", it.ai)) }
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("updated", c.updated).put("msgs", msgs))
        }
        val text = arr.toString()
        vm.viewModelScope.launch(Dispatchers.IO) { runCatching { file.writeText(text) } }
    }
}
