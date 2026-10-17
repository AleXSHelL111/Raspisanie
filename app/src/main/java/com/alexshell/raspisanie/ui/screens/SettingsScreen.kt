package com.alexshell.raspisanie.ui.screens

import com.alexshell.raspisanie.ui.tr
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.alexshell.raspisanie.ui.theme.ThemeMode
import com.alexshell.raspisanie.ui.theme.buildScheme
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
        item { SectionTitle(tr("Тема оформления"), color = Neon.Cyan) }
        item { ThemePicker(vm) }

        item { SectionTitle(tr("Номер группы"), color = Neon.Cyan) }
        item { GroupGrid(schedule?.groups.orEmpty(), vm.group, vm::selectGroup) }
        if (!schedule?.streams.isNullOrEmpty()) {
            item { SectionTitle(tr("Поток"), color = Neon.Cyan) }
            item { StreamPicker(schedule!!.streams, vm.stream, vm::selectStream) }
        }

        item { SectionTitle(tr("Уведомления"), color = Neon.Cyan) }
        item { NotificationsCard(vm) }

        val electives = schedule?.electives.orEmpty()
        if (electives.isNotEmpty()) {
            item { SectionTitle(tr("Дисциплины по выбору"), color = Neon.Lime) }
            item {
                NeonCard(accent = Neon.Lime) {
                    Text(
                        if (vm.chosenElectives.isEmpty()) tr("Отметьте свои — остальные исчезнут из расписания. Сейчас показаны все.")
                        else tr("В расписании только отмеченные дисциплины."),
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
                                    checkmarkColor = Neon.OnAccent,
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
                        TextButton(onClick = vm::clearElectives) { Text(tr("Показывать все"), color = Neon.Cyan) }
                    }
                }
            }
        }

        item { SectionTitle(tr("Мои правки расписания"), color = Neon.Amber) }
        item {
            NeonCard(accent = Neon.Amber) {
                val n = vm.editsCount()
                Text(
                    if (n == 0) tr("Правок нет. Чтобы изменить или удалить пару, нажмите на неё; добавить — во вкладке «Неделя».")
                    else tr("Изменённых, добавленных и удалённых занятий: {0}", n),
                    color = Neon.Text, fontSize = 14.sp,
                )
                if (n > 0) {
                    OutlinedButton(
                        onClick = vm::resetEdits,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Neon.Amber),
                    ) { Text(tr("Сбросить все правки"), color = Neon.Amber, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        item { SectionTitle(tr("Файл расписания"), color = Neon.Magenta) }
        item {
            NeonCard(accent = Neon.Magenta) {
                Text(schedule?.title ?: "—", fontWeight = FontWeight.SemiBold)
                schedule?.let {
                    Text(
                        tr("Начало семестра: {0} · занятий: {1}", "${it.semesterStart.dayMonth()} ${it.semesterStart.year}", it.lessons.size),
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Magenta, contentColor = Neon.OnAccent),
                ) { Text(tr("Загрузить новый .xlsx"), fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = vm::removeSchedule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Neon.Muted),
                ) { Text(tr("Удалить расписание"), color = Neon.Text) }
                Text(
                    tr("Подходит файл того же формата, что выкладывает учебный отдел. Можно также открыть .xlsx из Telegram или проводника через «Открыть с помощью»."),
                    color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (!schedule?.disciplines.isNullOrEmpty()) {
            item { SectionTitle(tr("Дисциплины и кафедры"), color = Neon.Violet) }
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
                            color = if (isSel) Neon.OnAccent else Neon.Text,
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


/** Режим (тёмная/светлая/системная) и акцентный цвет. */
@Composable
fun ThemePicker(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedTabs(
            items = ThemeMode.entries.map { tr(it.title) to true },
            selected = vm.themeMode.ordinal,
            onSelect = { vm.changeThemeMode(ThemeMode.entries[it]) },
            horizontalPadding = 0.dp,
        )
        Palettes.all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val sel = p.id == vm.themeId
                    // Цвета акцента так, как они будут выглядеть в текущем режиме.
                    val sc = buildScheme(p, Neon.isDark)
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (sel) Modifier.shadow(14.dp, shape, ambientColor = sc.primary, spotColor = sc.primary) else Modifier)
                            .clip(shape)
                            .background(Brush.verticalGradient(listOf(sc.surfaceHi, sc.bg)))
                            .border(if (sel) 2.dp else 1.dp, if (sel) sc.primary else sc.stroke, shape)
                            .clickable { vm.setTheme(p.id) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(sc.primary, sc.tertiary, sc.secondary, sc.primary)))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(p.name),
                            color = if (sel) sc.primary else sc.text,
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
fun SettingsOverlay(vm: AppViewModel, onImport: () -> Unit, onBack: () -> Unit, startSection: Int = 0) {
    var section by rememberSaveable { mutableIntStateOf(startSection) }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(140.dp) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Neon.Bg)) {
        Box(Modifier.fillMaxSize()) {
            NeonBackground {
                val pad = PaddingValues(top = headerHeight, bottom = bottom)
                if (section == 0) SettingsScreen(vm, pad, onImport) else ProfileScreen(vm, pad)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Neon.Bg)
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeonIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text(
                    tr("Настройки"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neon.Text,
                    style = androidx.compose.ui.text.TextStyle(shadow = Neon.glow(Neon.Violet, 20f)),
                )
            }
            SegmentedTabs(
                items = listOf(tr("Общие") to true, tr("Профиль") to true),
                selected = section,
                onSelect = { section = it },
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Stroke))
        }
    }
}

@Composable
fun SegmentedTabs(
    items: List<Pair<String, Boolean>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(horizontal = horizontalPadding)
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
                    // Ширина по длине подписи — длинные («Объявления») не переносятся.
                    .weight(if (items.size > 3) title.length.coerceAtLeast(3).toFloat() + 2f else 1f)
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
                    fontSize = if (items.size > 3) 13.sp else 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** Выбор потока: от него зависят лекции и физкультура. */
@Composable
fun StreamPicker(streams: List<Int>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        streams.forEach { n ->
            val isSel = n == selected
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .weight(1f)
                    .then(if (isSel) Modifier.shadow(16.dp, shape, ambientColor = Neon.Cyan, spotColor = Neon.Cyan) else Modifier)
                    .clip(shape)
                    .background(if (isSel) Brush.verticalGradient(listOf(Neon.Cyan, Neon.Cyan)) else Brush.verticalGradient(listOf(Neon.SurfaceHi, Neon.Surface)))
                    .border(1.dp, if (isSel) Neon.Cyan else Neon.Stroke, shape)
                    .clickable { onSelect(n) }
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$n",
                    color = if (isSel) Neon.OnAccent else Neon.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    tr("поток"),
                    color = if (isSel) Neon.OnAccent else Neon.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
