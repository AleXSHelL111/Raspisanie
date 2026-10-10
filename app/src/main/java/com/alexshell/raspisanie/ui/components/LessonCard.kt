package com.alexshell.raspisanie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.timer
import java.time.Duration
import java.time.LocalDateTime

enum class LessonState { PAST, NOW, SOON, LATER }

fun stateOf(lesson: Lesson, now: LocalDateTime): LessonState = when {
    now >= lesson.endAt -> LessonState.PAST
    now >= lesson.startAt -> LessonState.NOW
    Duration.between(now, lesson.startAt).toHours() < 3 && lesson.date == now.toLocalDate() -> LessonState.SOON
    else -> LessonState.LATER
}

@Composable
fun LessonCard(
    lesson: Lesson,
    now: LocalDateTime,
    homeworkCount: Int,
    homeworkPending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stateOf(lesson, now)
    val accent = Neon.kindColor(lesson.kind)
    NeonCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (state == LessonState.PAST) 0.5f else 1f),
        accent = accent,
        glow = state == LessonState.NOW,
        onClick = onClick,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Колонка времени
            Column(Modifier.width(62.dp)) {
                Text(
                    TimeSlot.fmt(lesson.start),
                    style = Neon.Digits.copy(shadow = Neon.glow(accent, 12f)),
                    color = accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    TimeSlot.fmt(lesson.end),
                    style = Neon.Digits,
                    color = Neon.Muted,
                    fontSize = 14.sp,
                )
            }
            // Светящаяся вертикальная черта
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeonChip(lesson.kind.title, accent)
                    if (lesson.online) NeonChip("онлайн", Neon.Amber)
                    if (lesson.specialTime) NeonChip("другое время", Neon.Amber)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    lesson.subject,
                    color = Neon.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                lesson.location?.let {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, null, tint = Neon.Muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = Neon.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (homeworkCount > 0) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Edit, null,
                            tint = if (homeworkPending) Neon.Magenta else Neon.Lime,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (homeworkPending) "Есть ДЗ к этой паре ($homeworkCount)" else "ДЗ выполнено",
                            color = if (homeworkPending) Neon.Magenta else Neon.Lime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                when (state) {
                    LessonState.NOW -> {
                        val total = Duration.between(lesson.startAt, lesson.endAt).seconds.toFloat()
                        val passed = Duration.between(lesson.startAt, now).seconds.toFloat()
                        Spacer(Modifier.height(10.dp))
                        NeonProgress(passed / total, color = accent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "идёт · до конца ${Duration.between(now, lesson.endAt).timer()}",
                            style = Neon.Digits,
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LessonState.SOON -> Text(
                        "начало через ${Duration.between(now, lesson.startAt).timer()}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = Neon.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    else -> {}
                }
            }
        }
    }
}
