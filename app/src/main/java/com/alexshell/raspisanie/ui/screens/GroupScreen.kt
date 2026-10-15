package com.alexshell.raspisanie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Absence
import com.alexshell.raspisanie.data.GroupMember
import com.alexshell.raspisanie.data.GroupState
import com.alexshell.raspisanie.ui.components.Avatar
import com.alexshell.raspisanie.ui.components.ChoiceChips
import com.alexshell.raspisanie.ui.components.FormSpacer
import com.alexshell.raspisanie.ui.components.GradientButton
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonChip
import com.alexshell.raspisanie.ui.components.NeonDatePickerDialog
import com.alexshell.raspisanie.ui.components.NeonField
import com.alexshell.raspisanie.ui.components.NeonIconButton
import com.alexshell.raspisanie.ui.components.NeonTimePickerDialog
import com.alexshell.raspisanie.ui.components.OutlineButton
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.relative
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr
import com.alexshell.raspisanie.ui.weekdayShort
import java.time.LocalDate

private const val NEWS_DAYS = 21
private val ROLE_SUGGESTIONS = listOf("Профорг", "Журнал", "Физорг", "Ответственный за ДЗ", "Культорг")

private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

private fun daysLeft(iso: String): Int {
    val t = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return NEWS_DAYS
    val passed = java.time.Duration.between(t, java.time.Instant.now()).toDays().toInt()
    return (NEWS_DAYS - passed).coerceIn(1, NEWS_DAYS)
}

private fun memberColor(m: GroupMember?) = when {
    m == null -> Neon.Stroke
    m.creator -> Neon.Amber
    m.starosta -> Neon.Magenta
    else -> Neon.Stroke
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(m: GroupMember) {
    if (!m.creator && !m.starosta && m.role.isBlank()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (m.creator) NeonChip("👑 " + tr("Создатель"), Neon.Amber)
        if (m.starosta) NeonChip("⭐ " + tr("Староста"), Neon.Magenta)
        if (m.role.isNotBlank()) NeonChip(m.role, Neon.Violet)
    }
}

private sealed interface GSheet {
    data class Member(val id: String) : GSheet
    data class Abs(val a: Absence) : GSheet
}

@Composable
fun GroupScreen(vm: AppViewModel, padding: PaddingValues, onOpenProfile: () -> Unit) {
    val c = vm.cloud
    var tab by rememberSaveable { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<GSheet?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    // Свежие данные при каждом открытии вкладки.
    LaunchedEffect(c.loggedIn) { c.pullGroup(quiet = true) }
    val d = c.groupState
    if (tab == 0 && d.group != null) LaunchedEffect(d.news) { c.markNewsSeen() }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Моя группа"), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Neon.glow(Neon.Cyan, 22f)),
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                if (c.loggedIn) NeonIconButton(Icons.Filled.Refresh, tr("Обновить")) { c.pullGroup() }
            }
        }
        if (!c.loggedIn) {
            item {
                NeonCard(accent = Neon.Cyan, glow = true) {
                    Text(tr("Группа и одногруппники"), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Neon.Cyan)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Войдите в профиль, чтобы создать группу или вступить в неё, читать объявления и вести учёт своих пропусков."), fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                    GradientButton(tr("Войти или зарегистрироваться"), onClick = onOpenProfile)
                }
            }
            return@LazyColumn
        }
        item {
            val dot = if (tab != 0 && c.unseenNews() > 0) " •" else ""
            SegmentedTabs(
                listOf(tr("Объявления") + dot to true, tr("Участники") to true, tr("Пропуски") to true),
                tab, { tab = it }, horizontalPadding = 0.dp,
            )
        }
        if (!c.groupLoaded) {
            item {
                if (c.groupError != null && !c.pulling) NeonCard(accent = Neon.Amber) {
                    Text(c.groupError.orEmpty(), color = Neon.Amber, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(tr("Повторить"), Neon.Amber) { c.pullGroup() }
                } else Text(tr("Загрузка…"), color = Neon.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
            }
            return@LazyColumn
        }
        when {
            tab == 2 -> absTab(vm, d) { sheet = GSheet.Abs(it) }
            d.group == null -> item { NoGroupCard(vm) }
            tab == 1 -> membersTab(vm, d, onMember = { sheet = GSheet.Member(it) }, onRename = { renaming = true }, onConfirm = { confirm = it })
            else -> newsTab(vm, d)
        }
    }

    when (val s = sheet) {
        is GSheet.Member -> d.members.firstOrNull { it.id == s.id }?.let { MemberSheet(vm, it) { sheet = null } }
        is GSheet.Abs -> AbsenceSheet(vm, s.a) { sheet = null }
        null -> {}
    }
    when (confirm) {
        "leave" -> ConfirmDialog(tr("Выйти из группы"), tr("Выйти из группы?"), { confirm = null }) { confirm = null; c.leaveGroup() }
        "delete" -> ConfirmDialog(tr("Удалить группу"), tr("Удалить группу для всех участников? Объявления тоже удалятся."), { confirm = null }) { confirm = null; c.deleteGroup() }
    }
    if (renaming) RenameDialog(d.group?.name.orEmpty(), { renaming = false }) { renaming = false; c.renameGroup(it) }
}

@Composable
private fun NoGroupCard(vm: AppViewModel) {
    val c = vm.cloud
    var name by rememberSaveable { mutableStateOf(vm.group?.let { "$it " + tr("группа") }.orEmpty()) }
    NeonCard(accent = Neon.Cyan, glow = true) {
        Text(tr("Вы пока не в группе"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Cyan)
        Spacer(Modifier.height(6.dp))
        Text(tr("Попросите создателя или старосту добавить вас по юзернейму {0} — или создайте группу сами.", "@" + c.user?.username.orEmpty()), color = Neon.Muted, fontSize = 14.sp)
        FormSpacer()
        NeonField(name, { name = it }, tr("Название группы"), placeholder = tr("Например: 313 группа, лечфак"))
        Spacer(Modifier.height(12.dp))
        GradientButton(tr("Создать группу"), enabled = c.busy == null, loading = c.busy == "g") { c.createGroup(name) }
    }
}

private fun LazyListScope.newsTab(vm: AppViewModel, d: GroupState) {
    val c = vm.cloud
    item { NewsComposer(vm) }
    if (d.news.isEmpty()) item {
        NeonCard(accent = Neon.Violet) {
            Text(tr("Объявлений пока нет"), color = Neon.Violet, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tr("Напишите первое — его увидят все одногруппники."), color = Neon.Muted)
        }
    }
    items(d.news, key = { "n" + it.id }) { n ->
        val m = d.members.firstOrNull { it.id == n.authorId }
        NeonCard(accent = memberColor(m)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(n.authorAvatar, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.author.ifBlank { "—" }, fontWeight = FontWeight.Bold)
                    m?.let { Badges(it) }
                }
                if (n.authorId == c.myId || c.canManage) {
                    IconButton(onClick = { c.deleteNews(n.id) }) { Icon(Icons.Filled.Delete, tr("Удалить"), tint = Neon.Dim) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(n.text, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("${fmtWhen(n.created)} · " + tr("удалится через {0} дн.", daysLeft(n.created)), color = Neon.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NewsComposer(vm: AppViewModel) {
    val c = vm.cloud
    var text by rememberSaveable { mutableStateOf("") }
    NeonCard(accent = Neon.Violet) {
        NeonField(text, { text = it }, tr("Объявление"), placeholder = tr("Написать объявление для группы…"), minLines = 3)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Объявления хранятся 3 недели, потом удаляются"), color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlineButton(tr("Опубликовать"), Neon.Cyan, Modifier.width(150.dp), enabled = text.isNotBlank() && c.busy == null, loading = c.busy == "g") {
                c.addNews(text) { text = "" }
            }
        }
    }
}

private fun LazyListScope.membersTab(
    vm: AppViewModel, d: GroupState,
    onMember: (String) -> Unit, onRename: () -> Unit, onConfirm: (String) -> Unit,
) {
    val c = vm.cloud
    val g = d.group ?: return
    val me = d.members.firstOrNull { it.id == c.myId }
    item {
        NeonCard(accent = Neon.Cyan, glow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Neon.Cyan, style = TextStyle(shadow = Neon.glow(Neon.Cyan, 20f)))
                    Text(tr("Участники · {0}", d.members.size), color = Neon.Muted, fontSize = 13.sp)
                }
                if (c.isCreator) NeonIconButton(Icons.Filled.Edit, tr("Переименовать"), onClick = onRename)
            }
            me?.let { Badges(it) }
        }
    }
    if (c.canManage) item { AddMemberCard(vm) }
    items(d.members, key = { "m" + it.id }) { m ->
        NeonCard(accent = memberColor(m), corner = 18.dp, onClick = { onMember(m.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.avatar)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.fullName + if (m.id == c.myId) " (" + tr("вы") + ")" else "", fontWeight = FontWeight.Bold)
                    Text("@" + m.username, color = Neon.Cyan, fontSize = 13.sp)
                    Badges(m)
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(4.dp))
        if (c.isCreator) OutlineButton(tr("Удалить группу"), Neon.Magenta) { onConfirm("delete") }
        else OutlineButton(tr("Выйти из группы"), Neon.Muted) { onConfirm("leave") }
    }
}

@Composable
private fun AddMemberCard(vm: AppViewModel) {
    val c = vm.cloud
    var username by rememberSaveable { mutableStateOf("") }
    NeonCard(accent = Neon.Lime) {
        Text(tr("Добавить одногруппника"), color = Neon.Lime, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonField(username, { username = it.trim() }, tr("Юзернейм"), Modifier.weight(1f), username = true)
            Spacer(Modifier.width(8.dp))
            OutlineButton("+", Neon.Lime, Modifier.width(64.dp), enabled = username.isNotBlank() && c.busy == null, loading = c.busy == "g") {
                c.addMember(username) { username = "" }
            }
        }
        Text(tr("Юзернейм указан в профиле одногруппника."), color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun LazyListScope.absTab(vm: AppViewModel, d: GroupState, onOpen: (Absence) -> Unit) {
    val list = d.absences
    val done = list.count { it.done }
    item {
        NeonCard(accent = Neon.Lime, glow = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(Triple(list.size, "Пропущено", Neon.Magenta), Triple(done, "Отработано", Neon.Lime), Triple(list.size - done, "Осталось", Neon.Amber))
                    .forEach { (n, t, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontSize = 30.sp, fontWeight = FontWeight.Black, color = color)
                            Text(tr(t), color = Neon.Muted, fontSize = 12.sp)
                        }
                    }
            }
            Text(tr("Пропуски видите только вы"), color = Neon.Dim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
    item {
        GradientButton("+  " + tr("Добавить пропуск")) {
            val today = LocalDate.now()
            onOpen(Absence("", today.toString(), vm.lessonsOn(today).firstOrNull()?.subject.orEmpty(), "", "", "", "", false))
        }
    }
    if (list.isEmpty()) item {
        NeonCard(accent = Neon.Lime) {
            Text(tr("Пропусков нет 🎉"), color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tr("Отмечайте пропуски и время отработки, чтобы ничего не забыть."), color = Neon.Muted)
        }
    }
    items(list, key = { "a" + it.id }) { a ->
        val color = if (a.done) Neon.Lime else Neon.Magenta
        val today = LocalDate.now()
        NeonCard(accent = color, corner = 18.dp, onClick = { onOpen(a) }) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(a.subject, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Пропуск") + ": " + (parseDate(a.date)?.relative(today) ?: a.date) + if (a.note.isNotBlank()) " · " + a.note else "",
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                    val mk = listOfNotNull(parseDate(a.makeupDate)?.relative(today), a.makeupTime.ifBlank { null }, a.makeupPlace.ifBlank { null }).joinToString(", ")
                    if (mk.isNotEmpty()) Text("📍 " + tr("Отработка") + ": " + mk, color = if (a.done) Neon.Muted else Neon.Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    else Text(tr("Отработка не назначена"), color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.layout.Box(Modifier.padding(top = 2.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = a.done,
                        onClick = { vm.cloud.saveAbsence(a.copy(done = !a.done)) {} },
                        label = { Text(if (a.done) "✓ " + tr("Отработано") else tr("Не отработано"), fontSize = 12.sp) },
                        colors = com.alexshell.raspisanie.ui.components.chipColors(color),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .padding(bottom = 24.dp)
        ) { content() }
    }
}

@Composable
private fun MemberSheet(vm: AppViewModel, u: GroupMember, onDismiss: () -> Unit) {
    val c = vm.cloud
    var role by rememberSaveable(u.id) { mutableStateOf(u.role) }
    var confirmRemove by remember { mutableStateOf(false) }
    FormSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.avatar, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(u.fullName, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("@" + u.username, color = Neon.Cyan, fontWeight = FontWeight.Bold)
            }
        }
        Badges(u)
        if (c.canManage) {
            SectionTitle(tr("Роль в группе"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonField(role, { role = it.take(40) }, tr("Роль"), Modifier.weight(1f), placeholder = tr("Например: Профорг, Журнал"))
                Spacer(Modifier.width(8.dp))
                OutlineButton("✓", Neon.Cyan, Modifier.width(64.dp), enabled = c.busy == null, loading = c.busy == "g") { c.setRole(u.id, role, onDismiss) }
            }
            Spacer(Modifier.height(8.dp))
            ChoiceChips(ROLE_SUGGESTIONS.map { tr(it) to tr(it) }, null) { role = it }
        }
        if (c.isCreator) {
            Spacer(Modifier.height(14.dp))
            if (u.starosta) OutlineButton(tr("Снять со старосты"), Neon.Magenta) { c.setStarosta("", onDismiss) }
            else OutlineButton("⭐ " + tr("Назначить старостой"), Neon.Magenta) { c.setStarosta(u.id, onDismiss) }
        }
        if (c.canManage && u.id != c.myId && !u.creator) {
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr("Удалить из группы"), Neon.Muted) { confirmRemove = true }
        }
    }
    if (confirmRemove) ConfirmDialog(tr("Удалить из группы"), tr("Удалить участника из группы?"), { confirmRemove = false }) {
        confirmRemove = false; c.removeMember(u.id, onDismiss)
    }
}

@Composable
private fun AbsenceSheet(vm: AppViewModel, initial: Absence, onDismiss: () -> Unit) {
    val c = vm.cloud
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var subject by rememberSaveable { mutableStateOf(initial.subject) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var mkDate by rememberSaveable { mutableStateOf(initial.makeupDate) }
    var mkTime by rememberSaveable { mutableStateOf(initial.makeupTime) }
    var mkPlace by rememberSaveable { mutableStateOf(initial.makeupPlace) }
    var done by rememberSaveable { mutableStateOf(initial.done) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val d = parseDate(date) ?: LocalDate.now()
    val daySubjects = vm.lessonsOn(d).map { it.subject }.distinct()
    FormSheet(onDismiss) {
        Text(
            if (initial.id.isEmpty()) tr("Добавить пропуск") else tr("Пропуск"),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Lime, style = TextStyle(shadow = Neon.glow(Neon.Lime, 18f)),
        )
        SectionTitle(tr("Когда пропустили"))
        OutlineButton("📅 ${d.weekdayShort()}, ${d.dayMonth()}", Neon.Cyan) { pick = "date" }
        FormSpacer()
        NeonField(subject, { subject = it }, tr("Что пропустили"), placeholder = tr("Предмет"))
        if (daySubjects.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ChoiceChips(daySubjects.map { it to it }, subject) { subject = it }
        }
        FormSpacer()
        NeonField(note, { note = it }, tr("Заметка"), placeholder = tr("Например: болел, есть справка"))
        SectionTitle(tr("Отработка"), color = Neon.Cyan)
        val mk = parseDate(mkDate)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(mk?.let { "📅 ${it.weekdayShort()}, ${it.dayMonth()}" } ?: tr("Дата"), Neon.Cyan, Modifier.weight(1.3f)) { pick = "mkDate" }
            OutlineButton(mkTime.ifBlank { tr("Время") }, Neon.Cyan, Modifier.weight(1f)) { pick = "mkTime" }
        }
        FormSpacer()
        NeonField(mkPlace, { mkPlace = it }, tr("Где"), placeholder = tr("Где: кафедра, аудитория"))
        if (mkDate.isNotEmpty() || mkTime.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = { mkDate = ""; mkTime = "" }) { Text(tr("Сбросить дату и время"), color = Neon.Muted) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Уже отработано"), Modifier.weight(1f), fontSize = 15.sp)
            Switch(checked = done, onCheckedChange = { done = it }, colors = SwitchDefaults.colors(checkedTrackColor = Neon.Lime, checkedThumbColor = Neon.OnAccent))
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(tr("Сохранить"), enabled = subject.isNotBlank() && c.busy == null, loading = c.busy == "g") {
            c.saveAbsence(Absence(initial.id, date, subject, note, mkDate, mkTime, mkPlace, done), onDismiss)
        }
        if (initial.id.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr("Удалить"), Neon.Muted) { confirmDelete = true }
        }
    }
    when (pick) {
        "date" -> NeonDatePickerDialog(tr("Когда пропустили"), d, onDismiss = { pick = null }, onConfirm = { date = it.toString(); pick = null })
        "mkDate" -> NeonDatePickerDialog(tr("Отработка"), mk ?: LocalDate.now(), onDismiss = { pick = null }, onConfirm = { mkDate = it.toString(); pick = null })
        "mkTime" -> {
            val init = mkTime.split(':').let { p -> (p.getOrNull(0)?.toIntOrNull() ?: 14) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }
            NeonTimePickerDialog(tr("Время"), init, onDismiss = { pick = null }, onConfirm = { m -> mkTime = "%02d:%02d".format(m / 60, m % 60); pick = null })
        }
    }
    if (confirmDelete) ConfirmDialog(tr("Удалить"), tr("Удалить пропуск?"), { confirmDelete = false }) {
        confirmDelete = false; c.deleteAbsence(initial.id, onDismiss)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neon.SurfaceHi,
        title = { Text(tr("Название группы")) },
        text = { NeonField(name, { name = it.take(60) }, tr("Название группы")) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(tr("Сохранить"), color = Neon.Cyan, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена"), color = Neon.Muted) } },
    )
}
