package com.alexshell.raspisanie.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alexshell.raspisanie.CloudState
import com.alexshell.raspisanie.data.CloudApi
import com.alexshell.raspisanie.data.CloudError
import com.alexshell.raspisanie.ui.I18n
import com.alexshell.raspisanie.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка группы (раз в 15 минут — минимум для Android):
 * новые объявления одногруппников и добавление в группу → уведомления.
 */
object GroupWatch {
    private const val WORK = "group-watch"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GroupWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)

    /**
     * Запоминает то, что пользователь уже увидел в приложении, чтобы не присылать это уведомлением.
     * [groupId] — текущая группа ("" — нет группы), [lastNews] — время последнего объявления.
     */
    fun markSeen(context: Context, groupId: String, lastNews: String?) {
        val p = prefs(context)
        val e = p.edit().putString("watchGroup", groupId)
        if (lastNews != null && lastNews > p.getString("watchSince", "").orEmpty()) e.putString("watchSince", lastNews)
        e.apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().remove("watchGroup").remove("watchSince").apply()
    }

    suspend fun check(context: Context) {
        val p = prefs(context)
        val token = p.getString("token", null) ?: return
        if (!Notifier.settings(context).group) return
        val url = CloudState.DEFAULT_SERVER.ifEmpty { p.getString("server", null).orEmpty() }
        if (url.isBlank()) return
        I18n.init(context)
        val first = !p.contains("watchSince")
        // Первый раз — «9999»: ничего старого не присылаем, дальше — время сервера (часы телефона могут спешить).
        val since = p.getString("watchSince", null) ?: "9999"
        val r = try {
            withContext(Dispatchers.IO) {
                CloudApi.call(url, JSONObject().put("action", "poll").put("token", token).put("since", since))
            }
        } catch (_: CloudError) {
            return
        }
        val group = r.optJSONObject("group")
        val groupId = group?.optString("id").orEmpty()
        val prevGroup = p.getString("watchGroup", null)
        // Добавили в группу (или в другую) — сообщаем, кроме самой первой проверки.
        if (group != null && prevGroup != null && prevGroup != groupId) {
            Notifier.showGroup(context, 7000, tr("Вас добавили в группу"), "«" + group.optString("name") + "»")
        }
        var last = r.optString("now").ifEmpty { since }
        r.optJSONArray("homework")?.takeIf { !first }?.let { hw ->
            for (i in 0 until hw.length()) {
                val h = hw.getJSONObject(i)
                Notifier.showGroup(
                    context, 7300 + (h.optString("id").hashCode() and 0xFFF),
                    "📝 " + tr("ДЗ для группы") + ": " + h.optString("subject"),
                    h.optString("text") + h.optString("author").let { if (it.isBlank()) "" else " — $it" },
                )
            }
        }
        val news = r.optJSONArray("news")
        if (news != null && !first) {
            for (i in 0 until news.length()) {
                val n = news.getJSONObject(i)
                Notifier.showGroup(
                    context, 7100 + (n.optString("id").hashCode() and 0xFFF),
                    "📣 " + n.optString("author").ifBlank { tr("Объявление") },
                    n.optString("text"),
                )
            }
        }
        if (news != null) for (i in 0 until news.length()) news.getJSONObject(i).optString("created").let { if (it > last) last = it }
        p.edit().putString("watchGroup", groupId).putString("watchSince", last).apply()
    }
}

class GroupWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { GroupWatch.check(applicationContext) }
        return Result.success()
    }
}
