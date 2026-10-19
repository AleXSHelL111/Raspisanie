package com.alexshell.raspisanie.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alexshell.raspisanie.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Переписка с KGMU AI: сообщения, «размышление» и печать ответа по буквам. */
class ChatState(private val vm: AppViewModel) {
    data class Msg(val user: Boolean, val text: String, val at: Long = System.currentTimeMillis())

    private val prefs = vm.getApplication<android.app.Application>().getSharedPreferences("ai", Context.MODE_PRIVATE)

    val messages = mutableStateListOf<Msg>().apply { addAll(load()) }

    /** Подпись «Думаю…», пока бот «размышляет»; null — не думает. */
    var thinking by mutableStateOf<String?>(null)
        private set
    /** Ответ, который сейчас печатается, и сколько символов уже видно. */
    var typing by mutableStateOf<String?>(null)
        private set
    var typed by mutableIntStateOf(0)
        private set

    /** Раздел, который попросили открыть («settings», «group», …) — его открывает экран. */
    var openRequest by mutableStateOf<String?>(null)

    val busy: Boolean get() = thinking != null || typing != null

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        messages += Msg(true, text)
        save()
        val reply = runCatching { Assistant.answer(vm, text) }
            .getOrElse { Assistant.Reply("Ой, что-то пошло не так 🙈 Попробуйте спросить по-другому.") }
        vm.viewModelScope.launch {
            // Пауза «на размышление» зависит от длины ответа.
            val wait = 650L + minOf(1200, reply.text.length * 5) + Random.nextLong(350)
            thinking = "Думаю"
            delay(wait * 45 / 100)
            thinking = reply.think ?: "Размышляю"
            delay(wait * 55 / 100)
            thinking = null
            val plain = Assistant.plainLength(reply.text)
            val dur = minOf(1600, plain * 12).toLong()
            typing = reply.text
            typed = 0
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < dur) {
                typed = (plain * (System.currentTimeMillis() - t0) / dur).toInt() + 1
                delay(24)
            }
            typing = null
            messages += Msg(false, reply.text)
            save()
            perform(reply.act)
        }
    }

    fun clear() {
        if (busy) return
        messages.clear()
        save()
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

    private fun load(): List<Msg> = runCatching {
        val arr = JSONArray(prefs.getString("msgs", "[]"))
        List(arr.length()) { i -> arr.getJSONObject(i).let { Msg(it.getBoolean("u"), it.getString("t"), it.optLong("at")) } }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        messages.takeLast(60).forEach { arr.put(JSONObject().put("u", it.user).put("t", it.text).put("at", it.at)) }
        prefs.edit().putString("msgs", arr.toString()).apply()
    }
}
