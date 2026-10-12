package com.alexshell.raspisanie.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.ui.components.LessonCard
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.weekdayFull
import com.alexshell.raspisanie.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun WeekScreen(
    vm: AppViewModel,
    now: LocalDateTime,
    padding: PaddingValues,
    onLesson: (Lesson) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val today = now.toLocalDate()
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val monday = selected.with(DayOfWeek.MONDAY)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
        .filter { it.dayOfWeek != DayOfWeek.SUNDAY || vm.lessonsOn(it).isNotEmpty() }
    val dayLessons = vm.lessonsOn(selected)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedEpoch = selected.minusWeeks(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущая неделя", tint = Neon.Cyan)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${monday.dayMonth()} — ${monday.plusDays(6).dayMonth()}",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    vm.schedule?.let {
                        Text(
                            "${it.weekType(monday)} неделя",
                            color = Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Magenta, 12f)),
                        )
                    }
                }
                IconButton(onClick = { selectedEpoch = selected.plusWeeks(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующая неделя", tint = Neon.Cyan)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { d ->
                    DayPill(
                        date = d,
                        selected = d == selected,
                        isToday = d == today,
                        count = vm.lessonsOn(d).size,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedEpoch = d.toEpochDay() },
                    )
                }
            }
        }
        if (selected != today) {
            item {
                TextButton(onClick = { selectedEpoch = today.toEpochDay() }) {
                    Text("↺ к сегодняшнему дню", color = Neon.Cyan)
                }
            }
        }
        item {
            SectionTitle("${selected.weekdayFull()}, ${selected.dayMonth()}", color = Neon.Cyan)
        }
        if (dayLessons.isEmpty()) {
            item {
                NeonCard(accent = Neon.Lime) {
                    Text("Выходной ✨", color = Neon.Lime, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("В этот день занятий нет", color = Neon.Muted)
                }
            }
        }
        items(dayLessons, key = { it.key }) { l ->
            val hw = vm.homeworkFor(l)
            LessonCard(l, now, hw.size, hw.any { !it.done }, onClick = { onLesson(l) })
        }
        item {
            OutlinedButton(
                onClick = { onAdd(selected) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.7f)),
            ) {
                Icon(Icons.Filled.Add, null, tint = Neon.Cyan)
                Spacer(Modifier.width(8.dp))
                Text("Добавить занятие на ${selected.weekdayShort().lowercase()}, ${selected.dayMonth()}", color = Neon.Cyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DayPill(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) Neon.Cyan else Neon.SurfaceHi, label = "pill")
    Column(
        modifier
            .then(if (selected) Modifier.shadow(14.dp, shape, ambientColor = Neon.Cyan, spotColor = Neon.Cyan) else Modifier)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (isToday && !selected) Neon.Magenta else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.weekdayShort(),
            color = if (selected) Neon.OnAccent else Neon.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            date.dayOfMonth.toString(),
            color = if (selected) Neon.OnAccent else Neon.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(count.coerceAtMost(4)) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) Neon.OnAccent else Neon.Magenta)
                )
            }
            if (count == 0) Spacer(Modifier.height(4.dp))
        }
    }
}
