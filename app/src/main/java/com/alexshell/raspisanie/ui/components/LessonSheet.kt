package com.alexshell.raspisanie.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
fun LessonSheet(lesson: Lesson, vm: AppViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = Neon.kindColor(lesson.kind)
    val next = remember(lesson, vm.lessons) { vm.nextOccurrence(lesson) }
    var toNext by rememberSaveable { mutableStateOf(next != null) }
    var text by rememberSaveable { mutableStateOf("") }
    val discipline = vm.schedule?.disciplineFor(lesson.subject)

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
                NeonChip(lesson.kind.title, accent, filled = true)
                if (lesson.online) NeonChip("онлайн", Neon.Amber)
                if (lesson.specialTime) NeonChip("другое время", Neon.Amber)
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

            InfoRow("Где", lesson.location)
            InfoRow("Кафедра / база", discipline?.department?.takeIf { it != lesson.location })
            InfoRow("Аттестация", discipline?.attestation)
            InfoRow("Из расписания", lesson.details.takeIf { it.isNotBlank() })

            // --- Домашние задания, привязанные к этой паре ---
            val hw = vm.homeworkFor(lesson)
            if (hw.isNotEmpty()) {
                SectionTitle("ДЗ к этой паре", color = Neon.Magenta)
                hw.forEach { h ->
                    HomeworkRow(h, onToggle = { vm.toggleDone(h.id) }, onDelete = { vm.deleteHomework(h.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionTitle("Добавить домашнее задание", color = Neon.Cyan)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (next != null) {
                    FilterChip(
                        selected = toNext,
                        onClick = { toNext = true },
                        label = { Text("К след. паре: ${next.date.weekdayShort()}, ${next.date.dayMonth()}") },
                        colors = chipColors(),
                    )
                }
                FilterChip(
                    selected = !toNext || next == null,
                    onClick = { toNext = false },
                    label = { Text("К этой паре") },
                    colors = chipColors(),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Например: выучить тему 5, решить задачи 1–10") },
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
                    vm.addHomework(target, text)
                    vm.message = "ДЗ добавлено на ${target.date.weekdayShort()}, ${target.date.dayMonth()}"
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Color.Black),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.padding(4.dp))
                Text("Сохранить ДЗ", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Neon.Cyan.copy(alpha = 0.18f),
    selectedLabelColor = Neon.Cyan,
    labelColor = Neon.Muted,
)

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(top = 12.dp)) {
        Text(label.uppercase(), color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Neon.Text, fontSize = 14.sp)
    }
}
