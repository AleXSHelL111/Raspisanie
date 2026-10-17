package com.alexshell.raspisanie.ui.components

import com.alexshell.raspisanie.ui.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.weekdayShort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonSheet(lesson: Lesson, vm: AppViewModel, onEdit: (Lesson) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = Neon.kindColor(lesson.kind)
    val next = remember(lesson, vm.lessons) { vm.nextOccurrence(lesson) }
    var toNext by rememberSaveable { mutableStateOf(next != null) }
    var text by rememberSaveable { mutableStateOf("") }
    var forGroup by rememberSaveable { mutableStateOf(false) }
    val discipline = vm.schedule?.disciplineFor(lesson.subject)
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Neon.Surface,
        contentColor = Neon.Text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonChip(tr(lesson.kind.title), accent, filled = true)
                if (lesson.online) NeonChip(tr("онлайн"), Neon.Amber)
                if (lesson.specialTime) NeonChip(tr("другое время"), Neon.Amber)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                lesson.fullSubject,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(accent, 20f)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${lesson.date.weekdayShort()}, ${lesson.date.dayMonth()} · " +
                    lesson.slots.joinToString("  ·  ") { it.toString() },
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (lesson.editId != null) {
                Spacer(Modifier.height(6.dp))
                NeonChip(tr("изменено вручную"), Neon.Amber)
            }
            DutyBlock(vm, lesson)
            InfoRow(tr("Где"), lesson.location)
            InfoRow(tr("Дисциплина по выбору"), lesson.elective?.takeIf { it != lesson.subject } ?: lesson.elective?.let { tr("да") })
            InfoRow(tr("Кафедра / база"), discipline?.department?.takeIf { it != lesson.location })
            InfoRow(tr("Аттестация"), discipline?.attestation)
            InfoRow(tr("Из расписания"), lesson.details.takeIf { it.isNotBlank() })

            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onEdit(lesson) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Neon.Cyan),
                ) {
                    Icon(Icons.Filled.Edit, null, tint = Neon.Cyan)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Изменить"), color = Neon.Cyan, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Neon.Magenta),
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Neon.Magenta)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Удалить"), color = Neon.Magenta, fontWeight = FontWeight.SemiBold)
                }
            }

            // --- Домашние задания, привязанные к этой паре ---
            val hw = vm.homeworkFor(lesson)
            if (hw.isNotEmpty()) {
                SectionTitle(tr("ДЗ к этой паре"), color = Neon.Magenta)
                hw.forEach { h ->
                    HomeworkRow(h, onToggle = { vm.toggleDone(h.id) }, onDelete = { vm.deleteHomework(h.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionTitle(tr("Добавить домашнее задание"), color = Neon.Cyan)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (next != null) {
                    FilterChip(
                        selected = toNext,
                        onClick = { toNext = true },
                        label = { Text(tr("К след. паре: {0}", "${next.date.weekdayShort()}, ${next.date.dayMonth()}")) },
                        colors = chipColors(),
                    )
                }
                FilterChip(
                    selected = !toNext || next == null,
                    onClick = { toNext = false },
                    label = { Text(tr("К этой паре")) },
                    colors = chipColors(),
                )
                if (vm.cloud.inGroup) {
                    FilterChip(
                        selected = forGroup,
                        onClick = { forGroup = !forGroup },
                        label = { Text("👥 " + tr("Для всей группы")) },
                        colors = chipColors(Neon.Violet),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("Например: выучить тему 5, решить задачи 1–10")) },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Neon.Cyan,
                    unfocusedBorderColor = Neon.Stroke,
                    cursorColor = Neon.Cyan,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val target = if (toNext && next != null) next else lesson
                    if (forGroup && vm.cloud.inGroup) {
                        // Для всей группы: задание придёт в личные ДЗ всем, включая автора.
                        vm.cloud.addGroupHomework(target, text)
                    } else {
                        vm.addHomework(target, text)
                        vm.message = tr("ДЗ добавлено на {0}", "${target.date.weekdayShort()}, ${target.date.dayMonth()}")
                    }
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnAccent),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.padding(4.dp))
                Text(tr("Сохранить ДЗ"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        val similar = vm.similarLessons(lesson).size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Neon.SurfaceHi,
            titleContentColor = Neon.Text,
            textContentColor = Neon.Muted,
            title = { Text(tr("Удалить занятие?")) },
            text = {
                Text(
                    if (similar > 1) tr("Можно удалить только эту пару или все такие пары «{0}» ({1}) — их {2}.", lesson.subject, "${lesson.date.weekdayShort().lowercase()}, ${lesson.timeText}", similar)
                    else "«${lesson.subject}», ${lesson.date.dayMonth()}"
                )
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = {
                        vm.deleteLesson(lesson, applyAll = false); confirmDelete = false; onDismiss()
                    }) { Text(tr("Только эту"), color = Neon.Magenta, fontWeight = FontWeight.Bold) }
                    if (similar > 1) {
                        TextButton(onClick = {
                            vm.deleteLesson(lesson, applyAll = true); confirmDelete = false; onDismiss()
                        }) { Text(tr("Все такие ({0})", similar), color = Neon.Magenta, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text(tr("Отмена"), color = Neon.Muted) }
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(top = 12.dp)) {
        Text(label.uppercase(), color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Neon.Text, fontSize = 14.sp)
    }
}


/** Дежурный на пару: видят все участники группы, назначают создатель и староста. */
@Composable
private fun DutyBlock(vm: AppViewModel, lesson: Lesson) {
    val c = vm.cloud
    if (!c.inGroup) return
    val duty = c.dutyFor(lesson)
    if (!c.canManage) {
        duty?.let { InfoRow("🧹 " + tr("Дежурный"), it.fullName) }
        return
    }
    Text(
        "🧹 " + tr("Дежурный на пару"), color = Neon.Muted, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        c.groupState.members.forEach { m ->
            FilterChip(
                selected = duty?.userId == m.id,
                onClick = { c.setDuty(lesson, m.id) },
                label = { Text(m.fullName) },
                colors = chipColors(Neon.Amber),
            )
        }
        if (duty != null) {
            FilterChip(selected = false, onClick = { c.setDuty(lesson, "") }, label = { Text("✕ " + tr("Снять")) }, colors = chipColors())
        }
    }
}
