package com.alexshell.raspisanie.ui.screens

import com.alexshell.raspisanie.ui.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.ui.components.HomeworkRow
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.humanize
import com.alexshell.raspisanie.ui.relative
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.weekdayShort
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun HomeworkScreen(vm: AppViewModel, now: LocalDateTime, padding: PaddingValues, onLesson: (Lesson) -> Unit) {
    val today = now.toLocalDate()
    val all = vm.groupHomework().sortedWith(compareBy({ it.dueDate }, { it.dueStart }))
    val active = all.filter { !it.done }
    val overdue = active.filter { it.dueDate.atTime(it.dueStart / 60, it.dueStart % 60) < now }
    val upcoming = active - overdue.toSet()
    val done = all.filter { it.done }.sortedByDescending { it.dueDate }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                tr("Домашние задания"),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Magenta, 20f)),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Text(
                tr("Нажмите на пару в расписании или «+», чтобы добавить ДЗ к следующей паре"),
                color = Neon.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        if (all.isEmpty()) {
            item {
                NeonCard(accent = Neon.Violet) {
                    Text(tr("Пока пусто"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Neon.Violet)
                    Text(tr("Все задания будут здесь — с обратным отсчётом до пары"), color = Neon.Muted)
                }
            }
        }
        if (upcoming.isNotEmpty()) item { SectionTitle(tr("Предстоящие · {0}", upcoming.size), color = Neon.Cyan) }
        items(upcoming, key = { it.id }) { h -> HwItem(h, vm, now, onLesson) }
        if (overdue.isNotEmpty()) item { SectionTitle(tr("Пара уже прошла · {0}", overdue.size), color = Neon.Amber) }
        items(overdue, key = { it.id }) { h -> HwItem(h, vm, now, onLesson) }
        if (done.isNotEmpty()) item { SectionTitle(tr("Выполнено · {0}", done.size), color = Neon.Lime) }
        items(done, key = { it.id }) { h -> HwItem(h, vm, now, onLesson) }
    }
}

@Composable
private fun HwItem(h: Homework, vm: AppViewModel, now: LocalDateTime, onLesson: (Lesson) -> Unit) {
    val due = h.dueDate.atTime(h.dueStart / 60, h.dueStart % 60)
    val left = Duration.between(now, due)
    val footer = when {
        h.done -> "${h.dueDate.relative(now.toLocalDate())}, ${TimeSlot.fmt(h.dueStart)}"
        left.isNegative -> tr("пара была {0}", h.dueDate.relative(now.toLocalDate()).lowercase())
        else -> "${h.dueDate.relative(now.toLocalDate())}, ${TimeSlot.fmt(h.dueStart)} · " + tr("через {0}", left.humanize())
    }
    val urgent = !h.done && !left.isNegative && left.toHours() < 24
    HomeworkRow(
        hw = h,
        onToggle = { vm.toggleDone(h.id) },
        onDelete = { vm.deleteHomework(h.id) },
        header = h.subject,
        headerColor = if (h.done) Neon.Lime else Neon.Cyan,
        footer = footer,
        footerColor = if (urgent) Neon.Magenta else Neon.Muted,
        onClick = vm.lessonByKey(h.lessonKey)?.let { l -> { onLesson(l) } },
    )
}

/** Диалог «быстрое ДЗ»: выбор предмета -> задание к ближайшей паре по нему. */
@Composable
fun AddHomeworkDialog(vm: AppViewModel, now: LocalDateTime, onDismiss: () -> Unit) {
    val subjects = remember(vm.lessons) { vm.subjects() }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val target = subject?.let { vm.nextBySubject(it, now) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neon.SurfaceHi,
        title = { Text(if (subject == null) tr("Предмет") else subject!!, fontWeight = FontWeight.Bold) },
        text = {
            if (subject == null) {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subjects.forEach { s ->
                        Text(
                            s,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { subject = s }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            color = Neon.Text,
                            fontSize = 15.sp,
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        target?.let { tr("К следующей паре: {0}", "${it.date.weekdayShort()}, ${it.date.dayMonth()}, ${it.timeText}") }
                            ?: tr("Будущих пар по предмету нет"),
                        color = if (target != null) Neon.Cyan else Neon.Amber,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = { Text(tr("Что задали?")) },
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Neon.Cyan,
                            unfocusedBorderColor = Neon.Stroke,
                            cursorColor = Neon.Cyan,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            if (subject != null) {
                TextButton(
                    enabled = target != null && text.isNotBlank(),
                    onClick = {
                        target?.let {
                            vm.addHomework(it, text)
                            vm.message = tr("ДЗ добавлено на {0}", "${it.date.weekdayShort()}, ${it.date.dayMonth()}")
                        }
                        onDismiss()
                    },
                ) { Text(tr("Сохранить"), color = Neon.Cyan, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (subject != null) subject = null else onDismiss() }) {
                Text(if (subject != null) tr("Назад") else tr("Отмена"), color = Neon.Muted)
            }
        },
    )
}
