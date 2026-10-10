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
        item {
            Text(
                "Настройки",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Violet, 20f)),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        item { SectionTitle("Моя группа", color = Neon.Cyan) }
        item { GroupGrid(schedule?.groups.orEmpty(), vm.group, vm::selectGroup) }

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
                Text(
                    if (vm.isImported) "Источник: загруженный файл" else "Источник: встроенное расписание",
                    color = Neon.Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Magenta, contentColor = Color.Black),
                ) { Text("Загрузить новый .xlsx", fontWeight = FontWeight.Bold) }
                if (vm.isImported) {
                    OutlinedButton(
                        onClick = vm::resetToBuiltIn,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Neon.Muted),
                    ) { Text("Вернуть встроенное", color = Neon.Text) }
                }
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
