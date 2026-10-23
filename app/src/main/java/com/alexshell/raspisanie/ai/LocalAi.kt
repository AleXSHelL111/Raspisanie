package com.alexshell.raspisanie.ai

import android.app.ActivityManager
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Локальная нейросеть Gemma 4 на движке LiteRT-LM (как в Google AI Edge Gallery):
 * модель скачивается по желанию пользователя и работает без интернета.
 */
class LocalAi(private val app: Application, private val scope: CoroutineScope) {

    /** Модели и требования — из списка моделей AI Edge Gallery. */
    data class Model(
        val id: String,
        val name: String,
        val file: String,
        val url: String,
        val sizeBytes: Long,
        val minRamGb: Int,
        val description: String,
    )

    sealed class Download {
        data object None : Download()
        data class Running(val done: Long, val total: Long) : Download()
        data object Ready : Download()
        data class Failed(val reason: String) : Download()
    }

    enum class EngineState { IDLE, LOADING, READY, ERROR }

    private val prefs = app.getSharedPreferences("local_ai", Context.MODE_PRIVATE)
    private val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val dir: File get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "models")

    /** Состояние загрузки по каждой модели. */
    val downloads = mutableStateMapOf<String, Download>()

    /** Выбранная модель; null — нейросеть выключена, отвечает обычный бот. */
    var selectedId by mutableStateOf(prefs.getString("selected", null))
        private set

    var engineState by mutableStateOf(EngineState.IDLE)
        private set
    var engineError by mutableStateOf<String?>(null)
        private set
    /** На чём работает модель: «GPU» или «CPU». */
    var engineBackend by mutableStateOf<String?>(null)
        private set

    private var engine: Engine? = null
    private var engineModelId: String? = null
    private val engineLock = Mutex()
    private val polls = mutableMapOf<String, Job>()

    /** Память телефона в ГБ (округлённо вверх, как пишут в характеристиках). */
    val ramGb: Int = run {
        val mi = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        Math.ceil(mi.totalMem / 1_073_741_824.0).toInt()
    }

    init {
        MODELS.forEach { m ->
            downloads[m.id] = when {
                prefs.getBoolean("ready_${m.id}", false) && fileOf(m).exists() -> Download.Ready
                prefs.getLong("dl_${m.id}", -1L) >= 0 -> Download.Running(0, m.sizeBytes).also { poll(m) }
                else -> Download.None
            }
        }
        selectedId?.let { if (downloads[it] != Download.Ready) setSelected(null) }
    }

    fun fileOf(m: Model) = File(dir, m.file)

    val selected: Model? get() = MODELS.firstOrNull { it.id == selectedId }

    /** Нейросеть включена и модель скачана. */
    val usable: Boolean get() = selected?.let { downloads[it.id] == Download.Ready } == true

    fun setSelected(id: String?) {
        if (id == selectedId) return
        selectedId = id
        prefs.edit().putString("selected", id).apply()
        if (engineModelId != id) release()
    }

    fun freeSpaceBytes(): Long = runCatching { dir.apply { mkdirs() }.usableSpace }.getOrDefault(0L)

    // ---------- Загрузка ----------

    fun download(m: Model) {
        if (downloads[m.id] is Download.Running || downloads[m.id] == Download.Ready) return
        dir.mkdirs()
        fileOf(m).delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("KGMU AI · ${m.name}")
            .setDescription("Нейросеть для чата")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, "models", m.file)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = runCatching { dm.enqueue(req) }.getOrElse {
            downloads[m.id] = Download.Failed(it.message ?: "Не удалось начать загрузку")
            return
        }
        prefs.edit().putLong("dl_${m.id}", id).putBoolean("ready_${m.id}", false).apply()
        downloads[m.id] = Download.Running(0, m.sizeBytes)
        poll(m)
    }

    fun cancel(m: Model) {
        val id = prefs.getLong("dl_${m.id}", -1L)
        if (id >= 0) runCatching { dm.remove(id) }
        polls.remove(m.id)?.cancel()
        prefs.edit().remove("dl_${m.id}").putBoolean("ready_${m.id}", false).apply()
        fileOf(m).delete()
        downloads[m.id] = Download.None
    }

    fun delete(m: Model) {
        if (engineModelId == m.id) release()
        if (selectedId == m.id) setSelected(null)
        cancel(m)
    }

    /** Следим за загрузкой через DownloadManager (он сам докачивает при обрыве сети). */
    private fun poll(m: Model) {
        polls[m.id]?.cancel()
        polls[m.id] = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val id = prefs.getLong("dl_${m.id}", -1L)
                if (id < 0) break
                val q = runCatching { dm.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
                val state = q?.use { c ->
                    if (!c.moveToFirst()) null
                    else Triple(
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    ) to c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
                if (state == null) {
                    finish(m, Download.Failed("Загрузка отменена"))
                    break
                }
                val (info, reason) = state
                val (status, done, total) = info
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val ok = fileOf(m).length() > 100_000_000L
                        finish(m, if (ok) Download.Ready else Download.Failed("Файл модели повреждён"))
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        fileOf(m).delete()
                        finish(m, Download.Failed(failReason(reason)))
                        break
                    }
                    else -> withContext(Dispatchers.Main) {
                        downloads[m.id] = Download.Running(done, if (total > 0) total else m.sizeBytes)
                    }
                }
                delay(700)
            }
        }
    }

    private suspend fun finish(m: Model, d: Download) = withContext(Dispatchers.Main) {
        prefs.edit().remove("dl_${m.id}").putBoolean("ready_${m.id}", d == Download.Ready).apply()
        downloads[m.id] = d
        // Первая скачанная модель сразу включается.
        if (d == Download.Ready && selectedId == null) setSelected(m.id)
    }

    private fun failReason(reason: Int) = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Недостаточно места на телефоне"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Нет доступа к памяти телефона"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "Обрыв связи — попробуйте ещё раз"
        in 400..599 -> "Сервер ответил ошибкой $reason"
        else -> "Не удалось скачать (код $reason)"
    }

    // ---------- Запуск модели ----------

    /** Загружает модель в память (первый раз — до десятка секунд). */
    private suspend fun ensureEngine(): Engine = engineLock.withLock {
        val m = selected ?: error("Модель не выбрана")
        engine?.let { if (engineModelId == m.id) return@withLock it }
        withContext(Dispatchers.Main) { engineState = EngineState.LOADING; engineError = null }
        closeEngine()
        try {
            val (e, label) = withContext(Dispatchers.Default) {
                // Сначала видеокарта (быстрее), при ошибке — процессор.
                var lastError: Throwable? = null
                for ((backend, label) in listOf<Pair<Backend, String>>(Backend.GPU() to "GPU", Backend.CPU() to "CPU")) {
                    try {
                        val e = Engine(EngineConfig(modelPath = fileOf(m).absolutePath, backend = backend, maxNumTokens = 4096, cacheDir = app.cacheDir.path))
                        e.initialize()
                        return@withContext e to label
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }
                throw lastError ?: IllegalStateException("Модель не запустилась")
            }
            engine = e
            engineModelId = m.id
            withContext(Dispatchers.Main) { engineState = EngineState.READY; engineBackend = label }
            e
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) { engineState = EngineState.ERROR; engineError = t.message ?: t.toString() }
            throw t
        }
    }

    /**
     * Ответ модели по частям. [history] — прошлые реплики чата (true — пользователь).
     * Бросает исключение, если модель не удалось запустить.
     */
    suspend fun reply(system: String, history: List<Pair<Boolean, String>>, prompt: String): Flow<String> {
        val e = ensureEngine()
        val conversation = withContext(Dispatchers.Default) {
            e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(system),
                    initialMessages = history.map { (user, text) -> if (user) Message.user(text) else Message.model(text) },
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                )
            )
        }
        return kotlinx.coroutines.flow.flow {
            try {
                conversation.sendMessageAsync(prompt).map { it.toString() }.collect { emit(it) }
            } finally {
                runCatching { conversation.close() }
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        engineModelId = null
    }

    /** Сразу освободить память (при закрытии приложения). */
    fun closeNow() = closeEngine()

    /** Выгрузить модель из памяти (при смене модели или выходе). */
    fun release() {
        scope.launch {
            engineLock.withLock { closeEngine() }
            engineState = EngineState.IDLE
            engineBackend = null
        }
    }

    companion object {
        val MODELS = listOf(
            Model(
                id = "gemma4-e2b",
                name = "Gemma 4 E2B",
                file = "gemma-4-E2B-it.litertlm",
                url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm?download=true",
                sizeBytes = 2_588_147_712L,
                minRamGb = 8,
                description = "Быстрая и лёгкая. Подходит большинству телефонов с 8 ГБ памяти.",
            ),
            Model(
                id = "gemma4-e4b",
                name = "Gemma 4 E4B",
                file = "gemma-4-E4B-it.litertlm",
                url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
                sizeBytes = 3_659_530_240L,
                minRamGb = 12,
                description = "Умнее, но тяжелее и медленнее. Для флагманов с 12 ГБ памяти.",
            ),
        )
    }
}
