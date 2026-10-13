package com.alexshell.raspisanie.ui.screens

import com.alexshell.raspisanie.ui.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.ui.clock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.alexshell.raspisanie.ui.components.LessonCard
import com.alexshell.raspisanie.ui.components.NeonIconButton
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonChip
import com.alexshell.raspisanie.ui.components.NeonProgress
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.humanize
import com.alexshell.raspisanie.ui.plural
import com.alexshell.raspisanie.ui.relative
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.timer
import com.alexshell.raspisanie.ui.weekdayFull
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun TodayScreen(
    vm: AppViewModel,
    now: LocalDateTime,
    padding: PaddingValues,
    onLesson: (Lesson) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit,
) {
    val today = now.toLocalDate()
    val todayLessons = vm.lessonsOn(today)
    val current = todayLessons.firstOrNull { now >= it.startAt && now < it.endAt }
    val next = vm.lessons.firstOrNull { it.startAt > now }
    // Следующий учебный день после сегодняшнего
    val nextDay = vm.lessons.firstOrNull { it.date > today }?.date
    val nextDayLessons = nextDay?.let { vm.lessonsOn(it) }.orEmpty()
    val pendingHw = vm.groupHomework().filter { !it.done && it.dueDate >= today }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Column { ClockHeader(vm, now, onOpenSettings) } }
        // В карточке пары — только ДЗ именно к этой паре.
        val heroLesson = current ?: next
        val heroHw = heroLesson?.let { l -> vm.homeworkFor(l).count { !it.done } } ?: 0
        item { HeroCard(current, next, now, heroHw, onLesson) }
        if (vm.schedule?.electives?.isNotEmpty() == true && vm.chosenElectives.isEmpty()) {
            item {
                NeonCard(accent = Neon.Lime, onClick = onOpenSettings) {
                    Text(tr("Выберите свою дисциплину по выбору"), color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        tr("Сейчас показаны все варианты сразу. Нажмите, чтобы оставить в расписании только вашу."),
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                }
            }
        }

        item { SectionTitle(if (todayLessons.isEmpty()) tr("Сегодня пар нет") else tr("Сегодня") + " · ${todayLessons.size} ${plural(todayLessons.size.toLong(), "занятие", "занятия", "занятий")}") }
        items(todayLessons, key = { "t" + it.key }) { l ->
            val hw = vm.homeworkFor(l)
            LessonCard(l, now, hw.size, hw.any { !it.done }, onClick = { onLesson(l) })
        }

        if (nextDay != null) {
            item {
                SectionTitle("${nextDay.relative(today)} · ${nextDay.weekdayFull()}", color = Neon.Violet)
            }
            items(nextDayLessons, key = { "n" + it.key }) { l ->
                val hw = vm.homeworkFor(l)
                LessonCard(l, now, hw.size, hw.any { !it.done }, onClick = { onLesson(l) })
            }
        }

        // Все невыполненные ДЗ — отдельно, внизу экрана.
        if (pendingHw.isNotEmpty()) {
            item { SectionTitle(tr("Невыполненные ДЗ · {0}", pendingHw.size), color = Neon.Magenta) }
            item {
                NeonCard(accent = Neon.Magenta, onClick = onOpenHomework) {
                    pendingHw.sortedWith(compareBy({ it.dueDate }, { it.dueStart })).take(4).forEachIndexed { i, h ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Text(h.subject, color = Neon.Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            h.text.lineSequence().first(),
                            color = Neon.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            tr("к паре: {0}", "${h.dueDate.relative(today)}, ${com.alexshell.raspisanie.data.TimeSlot.fmt(h.dueStart)}"),
                            color = Neon.Muted, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (pendingHw.size > 4) "Ещё ${pendingHw.size - 4} → все ДЗ" else tr("Открыть ДЗ →"),
                        color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockHeader(vm: AppViewModel, now: LocalDateTime, onOpenSettings: () -> Unit) {
    val today = now.toLocalDate()
    var langDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Настройки — слева, язык — справа, в одну строку: часы остаются на прежнем месте.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        NeonIconButton(Icons.Filled.Settings, tr("Настройки"), onClick = onOpenSettings)
        com.alexshell.raspisanie.ui.components.NeonLabelButton(
            com.alexshell.raspisanie.ui.I18n.lang.uppercase(), tr("Язык"), color = Neon.Violet,
        ) { langDialog = true }
    }
    if (langDialog) com.alexshell.raspisanie.ui.components.LanguageDialog { langDialog = false }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.clock(),
            style = Neon.Digits.copy(shadow = Neon.glow(Neon.Cyan, 28f)),
            color = Neon.Cyan,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
        )
        Text(
            "${today.weekdayFull()}, ${today.dayMonth()}",
            color = Neon.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.group?.let { NeonChip(tr("группа {0}", it), Neon.Cyan) }
            vm.schedule?.takeIf { it.hasWeekParity }?.let { NeonChip(tr("{0} неделя", it.weekType(today)), Neon.Magenta) }
        }
    }
}

@Composable
private fun HeroCard(current: Lesson?, next: Lesson?, now: LocalDateTime, pendingHw: Int, onLesson: (Lesson) -> Unit) {
    when {
        current != null -> NeonCard(accent = Neon.kindColor(current.kind), glow = true, onClick = { onLesson(current) }) {
            Text(tr("СЕЙЧАС ИДЁТ"), color = Neon.Magenta, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(current.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            val total = Duration.between(current.startAt, current.endAt).seconds.toFloat()
            val passed = Duration.between(current.startAt, now).seconds.toFloat()
            NeonProgress(passed / total, color = Neon.kindColor(current.kind))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("до конца") + " ", color = Neon.Muted, fontSize = 14.sp)
                Text(
                    Duration.between(now, current.endAt).timer(),
                    style = Neon.Digits.copy(shadow = Neon.glow(Neon.Magenta, 22f)),
                    color = Neon.Text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Перерывы внутри пары (например 10.40–12.10, 12.20–13.05)
            val slot = current.slots.firstOrNull { now.toLocalTime() < it.endTime }
            if (current.slots.size > 1 && slot != null) {
                val inBreak = now.toLocalTime() < slot.startTime
                Text(
                    if (inBreak) tr("перерыв · продолжение в {0}", slot.startTime)
                    else tr("часть {0} из {1} · до перерыва {2}", current.slots.indexOf(slot) + 1, current.slots.size, Duration.between(now.toLocalTime(), slot.endTime).timer()),
                    style = Neon.Digits,
                    color = Neon.Muted,
                    fontSize = 13.sp,
                )
            }
        }

        next != null -> NeonCard(accent = Neon.Cyan, glow = true, onClick = { onLesson(next) }) {
            val d = Duration.between(now, next.startAt)
            Text(
                if (next.date == now.toLocalDate()) tr("СЛЕДУЮЩАЯ ПАРА") else "БЛИЖАЙШАЯ ПАРА · ${next.date.relative(now.toLocalDate()).uppercase()}",
                color = Neon.Cyan, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(next.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${next.timeText} · ${tr(next.kind.title)}", color = Neon.Muted, fontSize = 14.sp)
            next.location?.let { Text(it, color = Neon.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("через") + " ", color = Neon.Muted, fontSize = 14.sp)
                Text(
                    if (d.toHours() < 24) d.timer() else d.humanize(),
                    style = Neon.Digits.copy(shadow = Neon.glow(Neon.Cyan, 22f)),
                    color = Neon.Text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (pendingHw > 0) {
                Spacer(Modifier.height(6.dp))
                NeonChip(tr("есть ДЗ к этой паре"), Neon.Magenta)
            }
        }

        else -> NeonCard(accent = Neon.Lime) {
            Text(tr("Семестр завершён 🎉"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Lime)
            Text(tr("Больше занятий в расписании нет"), color = Neon.Muted)
        }
    }
}
