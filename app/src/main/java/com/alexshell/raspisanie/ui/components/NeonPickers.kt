package com.alexshell.raspisanie.ui.components

import com.alexshell.raspisanie.ui.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alexshell.raspisanie.ui.RU
import com.alexshell.raspisanie.ui.theme.Neon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle

/** Общая неоновая «рамка» диалога. */
@Composable
private fun NeonDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(24.dp, shape, ambientColor = Neon.Cyan, spotColor = Neon.Violet)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Neon.SurfaceHi, Neon.Surface)))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)),
                    shape,
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title.uppercase(),
                color = Neon.Muted,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(tr("Отмена"), color = Neon.Muted) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet)))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(tr("Готово"), color = Neon.OnAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------- Время ----------------

/** Неоновый выбор времени: два «барабана» — часы и минуты. Значение — минуты от полуночи. */
@Composable
fun NeonTimePickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hour by remember { mutableIntStateOf(initial / 60) }
    var minute by remember { mutableIntStateOf(initial % 60) }
    NeonDialogFrame(title, onDismiss, onConfirm = { onConfirm(hour * 60 + minute) }) {
        Text(
            "%02d:%02d".format(hour, minute),
            style = Neon.Digits.copy(shadow = Neon.glow(Neon.Cyan, 26f)),
            color = Neon.Cyan,
            fontSize = 52.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center) {
            // Подсветка выбранной строки
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WHEEL_ITEM)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Neon.Cyan.copy(alpha = 0.10f))
                    .border(1.dp, Neon.Cyan.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wheel(count = 24, initial = hour, label = { "%02d".format(it) }, onChange = { hour = it })
                Text(":", color = Neon.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Wheel(count = 60, initial = minute, label = { "%02d".format(it) }, onChange = { minute = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        // Быстрый выбор минут
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 15, 30, 45).forEach { m ->
                val sel = minute == m
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Neon.Magenta else Neon.Magenta.copy(alpha = 0.12f))
                        .border(1.dp, Neon.Magenta.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .clickable { minute = m }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(":%02d".format(m), color = if (sel) Neon.OnAccent else Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val WHEEL_ITEM: Dp = 46.dp
private const val WHEEL_VISIBLE = 5

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, initial: Int, label: (Int) -> String, onChange: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val selected by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index ?: initial
        }
    }
    val reported = remember { mutableIntStateOf(initial) }
    LaunchedEffect(selected) {
        val v = selected.coerceIn(0, count - 1)
        reported.intValue = v
        onChange(v)
    }
    // Если значение поменяли снаружи (быстрые кнопки) — прокрутить барабан.
    LaunchedEffect(initial) { if (initial != reported.intValue) state.animateScrollToItem(initial) }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = Modifier.width(84.dp).height(WHEEL_ITEM * WHEEL_VISIBLE),
        contentPadding = PaddingValues(vertical = WHEEL_ITEM * (WHEEL_VISIBLE / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { i ->
            val distance = kotlin.math.abs(i - selected)
            Box(Modifier.height(WHEEL_ITEM).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    label(i),
                    modifier = Modifier.alpha(when (distance) { 0 -> 1f; 1 -> 0.55f; else -> 0.25f }),
                    style = Neon.Digits.copy(shadow = if (distance == 0) Neon.glow(Neon.Cyan, 14f) else null),
                    color = if (distance == 0) Neon.Text else Neon.Muted,
                    fontSize = if (distance == 0) 28.sp else 22.sp,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------------- Дата ----------------

/** Неоновый календарь. [marked] — дни, отмеченные точкой (например, дни с парами). */
@Composable
fun NeonDatePickerDialog(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    marked: Set<LocalDate> = emptySet(),
) {
    var selectedEpoch by remember { mutableLongStateOf(initial.toEpochDay()) }
    var monthIndex by remember { mutableIntStateOf(initial.year * 12 + initial.monthValue - 1) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val today = LocalDate.now()

    NeonDialogFrame(title, onDismiss, onConfirm = { onConfirm(selected) }) {
        Text(
            "${selected.dayOfWeek.getDisplayName(JTextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.uppercase() }}, " +
                "${selected.dayOfMonth} ${selected.month.getDisplayName(JTextStyle.FULL, RU)}",
            color = Neon.Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Neon.glow(Neon.Cyan, 20f)),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthIndex-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Предыдущий месяц"), tint = Neon.Cyan)
            }
            Text(
                "${month.month.getDisplayName(JTextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() }} ${month.year}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = Neon.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            IconButton(onClick = { monthIndex++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Следующий месяц"), tint = Neon.Cyan)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(JTextStyle.SHORT_STANDALONE, RU).take(2).replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (d == DayOfWeek.SUNDAY) Neon.Magenta.copy(alpha = 0.8f) else Neon.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val first = month.atDay(1)
        val shift = first.dayOfWeek.value - 1
        val cells = shift + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - shift + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            DayCell(
                                date = date,
                                isSelected = date == selected,
                                isToday = date == today,
                                hasMark = date in marked,
                                onClick = { selectedEpoch = date.toEpochDay() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, hasMark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .then(if (isSelected) Modifier.shadow(10.dp, CircleShape, ambientColor = Neon.Cyan, spotColor = Neon.Cyan) else Modifier)
            .clip(CircleShape)
            .background(if (isSelected) Neon.Cyan else Color.Transparent)
            .border(1.dp, if (isToday && !isSelected) Neon.Magenta else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = when {
                isSelected -> Neon.OnAccent
                date.dayOfWeek == DayOfWeek.SUNDAY -> Neon.Magenta.copy(alpha = 0.85f)
                else -> Neon.Text
            },
            fontSize = 15.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
        )
        if (hasMark) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Neon.OnAccent else Neon.Violet)
            )
        }
    }
}

// ---------------- Язык ----------------

/** Выбор языка интерфейса. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf(com.alexshell.raspisanie.ui.I18n.lang) }
    NeonDialogFrame(
        title = tr("Язык"),
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            com.alexshell.raspisanie.ui.I18n.set(context, selected)
            com.alexshell.raspisanie.notify.Notifier.reschedule(context)
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.alexshell.raspisanie.ui.I18n.languages.forEach { l ->
                val sel = l.code == selected
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (sel) Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet)) else Brush.verticalGradient(listOf(Neon.Surface, Neon.Surface)))
                        .border(1.dp, if (sel) Neon.Cyan else Neon.Muted.copy(alpha = 0.3f), shape)
                        .clickable { selected = l.code }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.flag, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        l.name,
                        color = if (sel) Neon.OnAccent else Neon.Text,
                        fontSize = 16.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(l.code.uppercase(), color = if (sel) Neon.OnAccent else Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
