package com.alexshell.raspisanie.ui.screens

import com.alexshell.raspisanie.ui.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonChip
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.theme.Neon

private data class Planned(val emoji: String, val title: String, val text: String, val color: Color)

/** Заготовка раздела «Моя группа» для будущих обновлений. */
@Composable
fun GroupScreen(vm: AppViewModel, padding: PaddingValues) {
    val planned = listOf(
        Planned("👥", tr("Одногруппники"), tr("Список группы и контакты старосты"), Neon.Cyan),
        Planned("📝", tr("Общие ДЗ"), tr("Задания, которые видит вся группа"), Neon.Magenta),
        Planned("📣", tr("Объявления"), tr("Переносы пар и новости от старосты"), Neon.Violet),
        Planned("📊", tr("Посещаемость"), tr("Отметки о пропусках и отработках"), Neon.Lime),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                tr("Моя группа"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Neon.glow(Neon.Cyan, 22f)),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        item {
            NeonCard(accent = Neon.Cyan, glow = true) {
                Text(
                    tr("Группа {0}", vm.group.orEmpty()),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Neon.Cyan,
                    style = TextStyle(shadow = Neon.glow(Neon.Cyan, 26f)),
                )
                Text(vm.schedule?.title.orEmpty(), color = Neon.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                NeonChip(tr("скоро"), Neon.Magenta, filled = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Раздел в разработке — здесь появятся функции для всей группы."),
                    color = Neon.Text, fontSize = 14.sp,
                )
            }
        }
        item { SectionTitle(tr("Что будет")) }
        items(planned) { (emoji, title, text, color) ->
            NeonCard(accent = color) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                        Text(text, color = Neon.Muted, fontSize = 13.sp)
                    }
                    Text("🔒", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}
