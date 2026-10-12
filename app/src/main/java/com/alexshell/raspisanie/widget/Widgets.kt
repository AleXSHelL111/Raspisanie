package com.alexshell.raspisanie.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.alexshell.raspisanie.MainActivity
import com.alexshell.raspisanie.R
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.Repository
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.notify.Notifier
import com.alexshell.raspisanie.ui.theme.Palettes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Обновление виджетов рабочего стола. */
object Widgets {

    private val executor = Executors.newSingleThreadExecutor()
    private const val ACTION_REFRESH = "com.alexshell.raspisanie.WIDGET_REFRESH"

    /** Обновить все виджеты в фоне. */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        executor.execute { runCatching { updateNow(app) } }
    }

    fun updateNow(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val nextIds = awm.getAppWidgetIds(ComponentName(context, NextLessonWidget::class.java))
        val todayIds = awm.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        if (nextIds.isEmpty() && todayIds.isEmpty()) return

        val palette = Palettes.byId(Repository.prefs(context).getString("theme", null))
        val accent = palette.primary.toArgb()
        val lessons = Repository.loadForBackground(context)?.first.orEmpty()
        val now = LocalDateTime.now()

        if (nextIds.isNotEmpty()) awm.updateAppWidget(nextIds, nextViews(context, lessons, now, accent))
        if (todayIds.isNotEmpty()) awm.updateAppWidget(todayIds, todayViews(context, lessons, now, accent))
        scheduleRefresh(context, lessons, now)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 10,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextViews(context: Context, lessons: List<Lesson>, now: LocalDateTime, accent: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_next)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        v.setTextColor(R.id.w_label, accent)
        val current = lessons.firstOrNull { now >= it.startAt && now < it.endAt }
        val next = current ?: lessons.firstOrNull { it.startAt > now }
        if (next == null) {
            v.setTextViewText(R.id.w_label, if (lessons.isEmpty()) "РАСПИСАНИЕ" else "ПАР БОЛЬШЕ НЕТ")
            v.setTextViewText(R.id.w_subject, if (lessons.isEmpty()) "Откройте приложение и загрузите файл" else "Семестр завершён 🎉")
            v.setTextViewText(R.id.w_time, "")
            v.setTextViewText(R.id.w_place, "")
            v.setViewVisibility(R.id.w_timer, View.GONE)
            v.setViewVisibility(R.id.w_timer_text, View.GONE)
            return v
        }
        val ongoing = current != null
        v.setTextViewText(
            R.id.w_label,
            when {
                ongoing -> "СЕЙЧАС ИДЁТ"
                next.date == now.toLocalDate() -> "СЛЕДУЮЩАЯ ПАРА"
                next.date == now.toLocalDate().plusDays(1) -> "ЗАВТРА"
                else -> "БЛИЖАЙШАЯ · ${dayLabel(next.date)}"
            },
        )
        v.setTextViewText(R.id.w_subject, next.subject)
        v.setTextViewText(R.id.w_time, "${next.timeText} · ${next.kind.title}")
        v.setTextViewText(R.id.w_place, next.location.orEmpty())

        val target = if (ongoing) next.endAt else next.startAt
        val left = Duration.between(now, target)
        if (left.toHours() < 24) {
            // Живой обратный отсчёт средствами системы — тикает без обновления виджета.
            val base = SystemClock.elapsedRealtime() + left.toMillis()
            v.setViewVisibility(R.id.w_timer, View.VISIBLE)
            v.setViewVisibility(R.id.w_timer_text, View.GONE)
            v.setChronometer(R.id.w_timer, base, if (ongoing) "до конца %s" else "через %s", true)
            v.setChronometerCountDown(R.id.w_timer, true)
        } else {
            v.setViewVisibility(R.id.w_timer, View.GONE)
            v.setViewVisibility(R.id.w_timer_text, View.VISIBLE)
            val d = left.toDays()
            val h = left.toHours() % 24
            v.setTextViewText(R.id.w_timer_text, "через $d д $h ч")
        }
        return v
    }

    private fun todayViews(context: Context, lessons: List<Lesson>, now: LocalDateTime, accent: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        v.setTextColor(R.id.w_title, accent)
        val today = now.toLocalDate()
        val todayLeft = lessons.filter { it.date == today && it.endAt > now }
        // Если сегодня пары закончились — показываем ближайший учебный день.
        val (title, list) = if (todayLeft.isNotEmpty() || lessons.none { it.date > today }) {
            "СЕГОДНЯ · ${dayLabel(today)}" to lessons.filter { it.date == today }
        } else {
            val day = lessons.first { it.date > today }.date
            (if (day == today.plusDays(1)) "ЗАВТРА · ${dayLabel(day)}" else dayLabel(day)) to lessons.filter { it.date == day }
        }
        v.setTextViewText(R.id.w_title, title)
        v.setViewVisibility(R.id.w_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.w_empty, if (lessons.isEmpty()) "Загрузите расписание в приложении" else "Пар нет 🎉")
        val rows = listOf(
            Triple(R.id.row1, R.id.time1, R.id.subj1), Triple(R.id.row2, R.id.time2, R.id.subj2),
            Triple(R.id.row3, R.id.time3, R.id.subj3), Triple(R.id.row4, R.id.time4, R.id.subj4),
            Triple(R.id.row5, R.id.time5, R.id.subj5), Triple(R.id.row6, R.id.time6, R.id.subj6),
        )
        rows.forEachIndexed { i, (row, time, subj) ->
            val l = list.getOrNull(i)
            if (l == null) {
                v.setViewVisibility(row, View.GONE)
            } else {
                val past = now >= l.endAt
                val live = now >= l.startAt && !past
                v.setViewVisibility(row, View.VISIBLE)
                v.setTextViewText(time, TimeSlot.fmt(l.start))
                v.setTextViewText(subj, (if (live) "▶ " else "") + l.subject)
                v.setTextColor(time, if (past) 0x66FFFFFF else accent)
                v.setTextColor(subj, if (past) 0x66FFFFFF else 0xFFF4F4FF.toInt())
            }
        }
        return v
    }

    private fun dayLabel(d: LocalDate): String {
        val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.forLanguageTag("ru"))
        return "${wd.uppercase().take(2)}, ${d.dayOfMonth}.${"%02d".format(d.monthValue)}"
    }

    /** Следующее обновление — на ближайшей границе пары или в полночь. */
    private fun scheduleRefresh(context: Context, lessons: List<Lesson>, now: LocalDateTime) {
        val boundaries = lessons.asSequence()
            .flatMap { sequenceOf(it.startAt, it.endAt) }
            .filter { it > now }
            .take(1)
            .toList()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
        val at = (boundaries + midnight).min()
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
        val pi = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        if (Notifier.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC, millis, pi)
    }
}

/** Общая часть провайдеров: обновление в фоне. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread {
            try {
                runCatching { Widgets.updateNow(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }
}

class NextLessonWidget : BaseWidget()

class TodayWidget : BaseWidget()

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                runCatching { Widgets.updateNow(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }
}
