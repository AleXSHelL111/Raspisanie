package com.alexshell.raspisanie.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexshell.raspisanie.MainActivity
import com.alexshell.raspisanie.R
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.Repository
import com.alexshell.raspisanie.data.TimeSlot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Уведомления:
 *  - за N минут до каждой пары (с ДЗ к этой паре, если есть);
 *  - вечером — сводка пар на завтра;
 *  - вечером — напоминание о невыполненном ДЗ на завтра.
 *
 * Ставится один точный будильник на ближайшее событие; когда он срабатывает,
 * показываем уведомления и планируем следующее.
 */
object Notifier {

    data class Settings(
        val beforeLesson: Boolean = true,
        val leadMinutes: Int = 15,
        val evening: Boolean = true,
        val eveningAt: Int = 20 * 60,
        val homework: Boolean = true,
    )

    private sealed class Event(val at: LocalDateTime) {
        class BeforeLesson(at: LocalDateTime, val lesson: Lesson) : Event(at)
        class Evening(at: LocalDateTime, val day: LocalDate) : Event(at)
        class HomeworkDue(at: LocalDateTime, val day: LocalDate) : Event(at)
    }

    const val CH_LESSON = "lessons"
    const val CH_DIGEST = "digest"
    const val CH_HOMEWORK = "homework"
    private const val ACTION_FIRE = "com.alexshell.raspisanie.FIRE"
    private const val KEY_LAST = "notif_last_fired"

    private val executor = Executors.newSingleThreadExecutor()

    // ---------- Настройки ----------

    fun settings(context: Context): Settings {
        val p = Repository.prefs(context)
        return Settings(
            beforeLesson = p.getBoolean("notif_before", true),
            leadMinutes = p.getInt("notif_lead", 15),
            evening = p.getBoolean("notif_evening", true),
            eveningAt = p.getInt("notif_evening_at", 20 * 60),
            homework = p.getBoolean("notif_hw", true),
        )
    }

    fun saveSettings(context: Context, s: Settings) {
        Repository.prefs(context).edit()
            .putBoolean("notif_before", s.beforeLesson)
            .putInt("notif_lead", s.leadMinutes)
            .putBoolean("notif_evening", s.evening)
            .putInt("notif_evening_at", s.eveningAt)
            .putBoolean("notif_hw", s.homework)
            .apply()
    }

    // ---------- Разрешения ----------

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun ignoresBattery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // ---------- Планирование ----------

    /** Пересчитать и поставить ближайший будильник (в фоне). */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        executor.execute { runCatching { scheduleNext(app) } }
        com.alexshell.raspisanie.widget.Widgets.updateAll(app)
    }

    private fun events(s: Settings, lessons: List<Lesson>, hw: List<Homework>, from: LocalDateTime): List<Event> {
        val out = ArrayList<Event>()
        val horizon = from.plusDays(8)
        if (s.beforeLesson) {
            lessons.asSequence()
                .filter { it.startAt > from && it.startAt < horizon }
                .forEach { out += Event.BeforeLesson(it.startAt.minusMinutes(s.leadMinutes.toLong()), it) }
        }
        val days = lessons.map { it.date }.toSet()
        val hwDays = hw.filter { !it.done }.map { it.dueDate }.toSet()
        var d = from.toLocalDate()
        while (d.atStartOfDay() < horizon) {
            val at = d.atTime(s.eveningAt / 60, s.eveningAt % 60)
            val tomorrow = d.plusDays(1)
            if (s.evening && tomorrow in days) out += Event.Evening(at, tomorrow)
            if (s.homework && tomorrow in hwDays) out += Event.HomeworkDue(at, tomorrow)
            d = d.plusDays(1)
        }
        return out
    }

    private fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context)
        am.cancel(pi)
        val (lessons, hw) = Repository.loadForBackground(context) ?: return
        val lastMs = Repository.prefs(context).getLong(KEY_LAST, 0L)
        val now = LocalDateTime.now()
        val from = maxOf(now, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastMs), ZoneId.systemDefault()))
        val next = events(settings(context), lessons, hw, now)
            .filter { it.at > from }
            .minByOrNull { it.at } ?: return
        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun firePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Будильник сработал: показать всё, что накопилось с прошлого раза, и запланировать дальше. */
    fun onAlarm(context: Context) {
        val p = Repository.prefs(context)
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        // Не показываем «старые» события, если телефон долго спал (максимум 3 часа назад).
        val last = p.getLong(KEY_LAST, 0L).coerceAtLeast(nowMs - 3 * 3600_000L)
        val lastAt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(last), ZoneId.systemDefault())
        Repository.loadForBackground(context)?.let { (lessons, hw) ->
            events(settings(context), lessons, hw, lastAt.minusDays(1))
                .filter { it.at > lastAt && !it.at.isAfter(now.plusSeconds(30)) }
                .forEach { e ->
                    when (e) {
                        is Event.BeforeLesson -> if (e.lesson.startAt > now.minusMinutes(5)) showLesson(context, e.lesson, hw)
                        is Event.Evening -> showEvening(context, e.day, lessons.filter { it.date == e.day })
                        is Event.HomeworkDue -> showHomework(context, e.day, hw.filter { !it.done && it.dueDate == e.day })
                    }
                }
        }
        p.edit().putLong(KEY_LAST, nowMs + 30_000).apply()
        scheduleNext(context)
    }

    // ---------- Показ ----------

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LESSON, "Перед парой", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Напоминание за несколько минут до начала пары" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DIGEST, "Пары на завтра", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Вечерняя сводка расписания на следующий день" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_HOMEWORK, "Домашние задания", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминание о невыполненном ДЗ" }
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(context: Context, id: Int, b: NotificationCompat.Builder) {
        if (!canPost(context)) return
        createChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(com.alexshell.raspisanie.ui.theme.Palettes.byId(Repository.prefs(context).getString("theme", null)).primary.toArgb())
        .setAutoCancel(true)
        .setContentIntent(openApp(context))

    private fun showLesson(context: Context, l: Lesson, hw: List<Homework>) {
        val mins = Duration.between(LocalDateTime.now(), l.startAt).toMinutes().coerceAtLeast(0)
        val title = if (mins > 0) "Через $mins мин: ${l.subject}" else "Начинается: ${l.subject}"
        val myHw = hw.filter { it.lessonKey == l.key && !it.done }
        val body = buildString {
            append("${l.timeText} · ${l.kind.title}")
            l.location?.let { append("\n📍 $it") }
            if (l.online) append("\n💻 онлайн")
            myHw.forEach { append("\n📝 ДЗ: ${it.text}") }
        }
        post(
            context, l.key.hashCode(),
            base(context, CH_LESSON)
                .setContentTitle(title)
                .setContentText(body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(l.startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setShowWhen(true),
        )
    }

    private fun showEvening(context: Context, day: LocalDate, lessons: List<Lesson>) {
        if (lessons.isEmpty()) return
        val first = lessons.first()
        val body = lessons.joinToString("\n") { "${TimeSlot.fmt(it.start)}  ${it.subject}" }
        post(
            context, 1001,
            base(context, CH_DIGEST)
                .setContentTitle("Завтра ${lessons.size} ${plural(lessons.size, "пара", "пары", "пар")}, первая в ${TimeSlot.fmt(first.start)}")
                .setContentText(first.subject)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)),
        )
    }

    private fun showHomework(context: Context, day: LocalDate, items: List<Homework>) {
        if (items.isEmpty()) return
        val body = items.joinToString("\n") { "• ${it.subject}: ${it.text}" }
        post(
            context, 1002,
            base(context, CH_HOMEWORK)
                .setContentTitle("ДЗ на завтра: ${items.size}")
                .setContentText(items.first().let { "${it.subject}: ${it.text}" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)),
        )
    }

    fun showTest(context: Context) {
        post(
            context, 999,
            base(context, CH_LESSON)
                .setContentTitle("Уведомления работают ✨")
                .setContentText("Так будет выглядеть напоминание перед парой")
                .setPriority(NotificationCompat.PRIORITY_HIGH),
        )
    }

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }
}
