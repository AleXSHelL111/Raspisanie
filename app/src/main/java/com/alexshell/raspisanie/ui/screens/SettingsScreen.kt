package com.alexshell.raspisanie.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import com.alexshell.raspisanie.ui.components.NeonBackground
import com.alexshell.raspisanie.ui.components.NeonIconButton
import com.alexshell.raspisanie.ui.theme.Palettes
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.theme.Neon

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues, onImport: () -> Unit) {
    val schedule = vm.schedule
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Тема оформления", color = Neon.Cyan) }
        item { ThemePicker(vm.themeId, vm::setTheme) }

        item { SectionTitle("Номер группы", color = Neon.Cyan) }
        item { GroupGrid(schedule?.groups.orEmpty(), vm.group, vm::selectGroup) }

        item { SectionTitle("Уведомления", color = Neon.Cyan) }
        item { NotificationsCard(vm) }

        val electives = schedule?.electives.orEmpty()
        if (electives.isNotEmpty()) {
            item { SectionTitle("Дисциплины по выбору", color = Neon.Lime) }
            item {
                NeonCard(accent = Neon.Lime) {
                    Text(
                        if (vm.chosenElectives.isEmpty()) "Отметьте свои — остальные исчезнут из расписания. Сейчас показаны все."
                        else "В расписании только отмеченные дисциплины.",
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    electives.forEach { e ->
                        val on = e in vm.chosenElectives
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.toggleElective(e) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = { vm.toggleElective(e) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Neon.Lime,
                                    uncheckedColor = Neon.Muted,
                                    checkmarkColor = Color.Black,
                                ),
                            )
                            Text(
                                e,
                                color = if (on) Neon.Lime else Neon.Text,
                                fontSize = 15.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    if (vm.chosenElectives.isNotEmpty()) {
                        TextButton(onClick = vm::clearElectives) { Text("Показывать все", color = Neon.Cyan) }
                    }
                }
            }
        }

        item { SectionTitle("Мои правки расписания", color = Neon.Amber) }
        item {
            NeonCard(accent = Neon.Amber) {
                val n = vm.editsCount()
                Text(
                    if (n == 0) "Правок нет. Чтобы изменить или удалить пару, нажмите на неё; добавить — во вкладке «Неделя»."
                    else "Изменённых, добавленных и удалённых занятий: $n",
                    color = Neon.Text, fontSize = 14.sp,
                )
                if (n > 0) {
                    OutlinedButton(
                        onClick = vm::resetEdits,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Neon.Amber),
                    ) { Text("Сбросить все правки", color = Neon.Amber, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        item { SectionTitle("Файл расписания", color = Neon.Magenta) }
        item {
            NeonCard(accent = Neon.Magenta) {
                Text(schedule?.title ?: "—", fontWeight = FontWeight.SemiBold)
                schedule?.let {
                    Text(
                        "Начало семестра: ${it.semesterStart.dayMonth()} ${it.semesterStart.year} · занятий: ${it.lessons.size}",
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Magenta, contentColor = Color.Black),
                ) { Text("Загрузить новый .xlsx", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = vm::removeSchedule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Neon.Muted),
                ) { Text("Удалить расписание", color = Neon.Text) }
                Text(
                    "Подходит файл того же формата, что выкладывает учебный отдел. " +
                        "Можно также открыть .xlsx из Telegram или проводника через «Открыть с помощью».",
                    color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (!schedule?.disciplines.isNullOrEmpty()) {
            item { SectionTitle("Дисциплины и кафедры", color = Neon.Violet) }
            schedule!!.disciplines.distinctBy { it.name }.forEach { d ->
                item {
                    NeonCard(accent = Neon.Violet, corner = 18.dp) {
                        Text(d.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (d.department.isNotBlank()) Text(d.department, color = Neon.Muted, fontSize = 13.sp)
                        d.attestation?.let {
                            Text(it.replaceFirstChar { c -> c.uppercase() }, color = Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupGrid(groups: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { g ->
                    val isSel = g == selected
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.3f)
                            .then(if (isSel) Modifier.shadow(16.dp, shape, ambientColor = Neon.Cyan, spotColor = Neon.Cyan) else Modifier)
                            .clip(shape)
                            .background(if (isSel) Neon.Cyan else Neon.SurfaceHi)
                            .border(1.dp, if (isSel) Neon.Cyan else Neon.Stroke, shape)
                            .clickable { onSelect(g) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            g,
                            color = if (isSel) Color.Black else Neon.Text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


/** Сетка тем: кружок-градиент из цветов темы и название. */
@Composable
fun ThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palettes.all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val sel = p.id == selectedId
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (sel) Modifier.shadow(14.dp, shape, ambientColor = p.primary, spotColor = p.primary) else Modifier)
                            .clip(shape)
                            .background(Brush.verticalGradient(listOf(p.surfaceHi, p.bg)))
                            .border(if (sel) 2.dp else 1.dp, if (sel) p.primary else p.stroke, shape)
                            .clickable { onSelect(p.id) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(p.primary, p.tertiary, p.secondary, p.primary)))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.name,
                            color = if (sel) p.primary else Neon.Text,
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Экран настроек: шапка, разделы «Общие» и «Профиль». */
@Composable
fun SettingsOverlay(vm: AppViewModel, onImport: () -> Unit, onBack: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Neon.Bg)) {
        NeonBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Шапка: кнопка и заголовок по центру строки, с отступами сверху и снизу.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeonIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClick = onBack)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Настройки",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Violet, 20f)),
                    )
                }
                SegmentedTabs(
                    items = listOf("Общие" to true, "Профиль 🔒" to true),
                    selected = section,
                    onSelect = { section = it },
                )
                Box(Modifier.weight(1f)) {
                    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    if (section == 0) SettingsScreen(vm, PaddingValues(bottom = bottom), onImport)
                    else ProfileLocked(bottom)
                    // Мягкое затухание у верхнего края списка вместо резкого обреза.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(Brush.verticalGradient(listOf(Neon.Bg.copy(alpha = 0.9f), Color.Transparent)))
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(items: List<Pair<String, Boolean>>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Neon.Surface)
            .border(1.dp, Neon.Stroke, shape)
            .padding(4.dp),
    ) {
        items.forEachIndexed { i, (title, _) ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (sel) Brush.horizontalGradient(listOf(Neon.Cyan.copy(alpha = 0.30f), Neon.Violet.copy(alpha = 0.25f)))
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .border(1.dp, if (sel) Neon.Cyan.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    color = if (sel) Neon.Cyan else Neon.Muted,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** Раздел «Профиль» — пока недоступен. */
@Composable
private fun ProfileLocked(bottom: androidx.compose.ui.unit.Dp) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            NeonCard(accent = Neon.Violet, glow = true) {
                Text("🔒", fontSize = 36.sp)
                Spacer(Modifier.height(8.dp))
                Text("Профиль пока недоступен", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Violet)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Раздел появится в следующих обновлениях.",
                    color = Neon.Muted, fontSize = 14.sp,
                )
            }
        }
        item { SectionTitle("Что здесь будет") }
        listOf(
            "Имя и аватар" to "Ваш профиль студента",
            "Синхронизация" to "Перенос ДЗ и настроек на другой телефон",
            "Резервная копия" to "Сохранение и восстановление данных",
        ).forEach { (title, text) ->
            item {
                NeonCard(accent = Neon.Stroke) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Neon.Muted)
                    Text(text, color = Neon.Dim, fontSize = 13.sp)
                }
            }
        }
    }
}
