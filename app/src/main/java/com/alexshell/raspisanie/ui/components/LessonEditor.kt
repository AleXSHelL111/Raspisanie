package com.alexshell.raspisanie.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.LessonKind
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.weekdayShort
import java.time.LocalDate

/**
 * Редактор занятия. [original] == null — создание нового занятия на дату [initialDate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonEditor(vm: AppViewModel, original: Lesson?, initialDate: LocalDate, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val subjects = remember(vm.lessons) { vm.subjects() }
    val lastDate = remember(vm.lessons) { vm.lessons.lastOrNull()?.date ?: initialDate.plusMonths(3) }

    var subject by rememberSaveable { mutableStateOf(original?.subject.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: LessonKind.PRACTICE) }
    var dateEpoch by rememberSaveable { mutableLongStateOf((original?.date ?: initialDate).toEpochDay()) }
    var start by rememberSaveable { mutableIntStateOf(original?.start ?: 8 * 60) }
    var end by rememberSaveable { mutableIntStateOf(original?.end ?: (9 * 60 + 30)) }
    var location by rememberSaveable { mutableStateOf(original?.location.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(if (original?.editId != null) original.details else "") }
    var applyAll by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var untilEpoch by rememberSaveable { mutableLongStateOf(lastDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val until = LocalDate.ofEpochDay(untilEpoch)
    val similar = remember(original, vm.lessons) { original?.let { vm.similarLessons(it).size } ?: 0 }

    // Свои неоновые диалоги выбора даты и времени.
    var datePick by remember { mutableStateOf<Pair<LocalDate, (LocalDate) -> Unit>?>(null) }
    var timePick by remember { mutableStateOf<Pair<Int, (Int) -> Unit>?>(null) }
    val lessonDays = remember(vm.lessons) { vm.lessons.map { it.date }.toSet() }

    fun pickDate(initial: LocalDate, set: (LocalDate) -> Unit) {
        datePick = initial to set
    }

    fun pickTime(initial: Int, set: (Int) -> Unit) {
        timePick = initial to set
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Neon.Cyan,
        unfocusedBorderColor = Neon.Stroke,
        cursorColor = Neon.Cyan,
        focusedTextColor = Neon.Text,
        unfocusedTextColor = Neon.Text,
        focusedLabelColor = Neon.Cyan,
        unfocusedLabelColor = Neon.Muted,
    )

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
                .imePadding(),
        ) {
            Text(
                if (original == null) "Новое занятие" else "Изменить занятие",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Neon.Text,
                style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Cyan, 18f)),
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Предмет") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            if (subjects.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subjects.forEach { s ->
                        FilterChip(
                            selected = s == subject,
                            onClick = { subject = s },
                            label = { Text(if (s.length > 28) s.take(26) + "…" else s) },
                            colors = chipColors(),
                        )
                    }
                }
            }

            Label("Тип занятия")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LessonKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.title) },
                        colors = chipColors(Neon.kindColor(k)),
                    )
                }
            }

            Label("Дата и время")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerButton("${date.weekdayShort()}, ${date.dayMonth()}", Modifier.weight(1.4f)) {
                    pickDate(date) { dateEpoch = it.toEpochDay() }
                }
                PickerButton(TimeSlot.fmt(start), Modifier.weight(1f)) {
                    pickTime(start) { v ->
                        val len = end - start
                        start = v
                        if (end <= start) end = (start + len.coerceAtLeast(45)).coerceAtMost(23 * 60 + 59)
                    }
                }
                PickerButton(TimeSlot.fmt(end), Modifier.weight(1f)) { pickTime(end) { end = it } }
            }
            if (end <= start) {
                Text("Конец пары должен быть позже начала", color = Neon.Magenta, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Где (корпус, аудитория)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )

            if (original == null) {
                SwitchRow("Повторять каждую неделю", repeat) { repeat = it }
                if (repeat) {
                    PickerButton("до ${until.weekdayShort()}, ${until.dayMonth()}", Modifier.fillMaxWidth()) {
                        pickDate(until) { untilEpoch = it.toEpochDay() }
                    }
                }
            } else if (similar > 1) {
                SwitchRow("Применить ко всем таким парам ($similar шт.)", applyAll) { applyAll = it }
                if (applyAll) {
                    Text(
                        "Изменятся предмет, тип, время и место у всех пар «${original.subject}» " +
                            "по ${original.date.weekdayShort().lowercase()} в ${TimeSlot.fmt(original.start)}, начиная с этой. Даты останутся прежними.",
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = subject.isNotBlank() && end > start,
                onClick = {
                    val slots = if (original != null && original.start == start && original.end == end) original.slots
                    else listOf(TimeSlot(start, end))
                    val edited = Lesson(
                        date = date,
                        slots = slots,
                        subject = subject.trim(),
                        fullSubject = if (original != null && original.subject == subject.trim()) original.fullSubject else subject.trim(),
                        kind = kind,
                        groups = emptySet(),
                        location = location.trim().ifEmpty { null },
                        details = note.trim(),
                        online = original?.online == true && original.date == date,
                        specialTime = false,
                        elective = original?.elective?.takeIf { original.subject == subject.trim() },
                    )
                    vm.saveLesson(original, edited, applyAll, if (original == null && repeat) until else null)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Neon.Cyan,
                    contentColor = Neon.OnAccent,
                    disabledContainerColor = Neon.Stroke,
                    disabledContentColor = Neon.Muted,
                ),
            ) { Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }

    datePick?.let { (initial, set) ->
        NeonDatePickerDialog(
            title = "Дата",
            initial = initial,
            marked = lessonDays,
            onDismiss = { datePick = null },
            onConfirm = { set(it); datePick = null },
        )
    }
    timePick?.let { (initial, set) ->
        NeonTimePickerDialog(
            title = "Время",
            initial = initial,
            onDismiss = { timePick = null },
            onConfirm = { set(it); timePick = null },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = Neon.Muted,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun PickerButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.6f)),
    ) {
        Text(text, color = Neon.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Neon.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Neon.OnAccent,
                checkedTrackColor = Neon.Cyan,
                uncheckedThumbColor = Neon.Muted,
                uncheckedTrackColor = Neon.SurfaceHi,
                uncheckedBorderColor = Neon.Stroke,
            ),
        )
    }
}

@Composable
fun chipColors(color: Color = Neon.Cyan) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = color.copy(alpha = 0.22f),
    selectedLabelColor = color,
    labelColor = Neon.Text,
    containerColor = Neon.SurfaceHi,
)
