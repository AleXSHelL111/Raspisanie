package com.alexshell.raspisanie.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.AttRecord
import com.alexshell.raspisanie.data.CloudUser
import com.alexshell.raspisanie.data.GroupData
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.ui.components.Avatar
import com.alexshell.raspisanie.ui.components.ChoiceChips
import com.alexshell.raspisanie.ui.components.FormSpacer
import com.alexshell.raspisanie.ui.components.GradientButton
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonChip
import com.alexshell.raspisanie.ui.components.NeonDatePickerDialog
import com.alexshell.raspisanie.ui.components.NeonField
import com.alexshell.raspisanie.ui.components.NeonIconButton
import com.alexshell.raspisanie.ui.components.OutlineButton
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.dayMonth
import com.alexshell.raspisanie.ui.relative
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr
import com.alexshell.raspisanie.ui.weekdayShort
import java.time.LocalDate

private val NEWS_KIND = listOf("news" to "Новость", "move" to "Перенос пары", "important" to "Важно")
private val ATT_STATUS = listOf("absent" to "Пропуск", "excused" to "Уважительная", "done" to "Отработано")

private fun newsColor(k: String) = when (k) { "move" -> Neon.Amber; "important" -> Neon.Magenta; else -> Neon.Cyan }
private fun attColor(s: String) = when (s) { "excused" -> Neon.Violet; "done" -> Neon.Lime; else -> Neon.Magenta }
private fun roleColor(r: String) = when (r) { "starosta" -> Neon.Magenta; "deputy" -> Neon.Violet; else -> Neon.Muted }

/** Ссылка для связи: @ник → Telegram, номер → звонок, почта → письмо. */
fun contactUri(c: String): Uri? {
    val s = c.trim()
    return when {
        Regex("""^@\w{3,}$""").matches(s) -> Uri.parse("https://t.me/" + s.drop(1))
        s.startsWith("http://") || s.startsWith("https://") -> Uri.parse(s)
        Regex("""^\+?[\d\s()-]{7,}$""").matches(s) -> Uri.parse("tel:" + s.filter { it.isDigit() || it == '+' })
        Regex("""^[^@\s]+@[^@\s]+\.\w+$""").matches(s) -> Uri.parse("mailto:$s")
        else -> null
    }
}

private sealed interface GroupSheet {
    data object News : GroupSheet
    data object Share : GroupSheet
    data object Att : GroupSheet
    data class Member(val id: String) : GroupSheet
}

@Composable
fun GroupScreen(vm: AppViewModel, padding: PaddingValues, onOpenProfile: () -> Unit) {
    val c = vm.cloud
    var tab by rememberSaveable { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<GroupSheet?>(null) }
    LaunchedEffect(Unit) { c.pullIfStale() }
    val d = c.data
    if (tab == 0 && d != null) LaunchedEffect(d) { c.markNewsSeen() }

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
                    tr("Моя группа"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Neon.glow(Neon.Cyan, 22f)),
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                if (c.loggedIn) NeonIconButton(Icons.Filled.Refresh, tr("Обновить")) { c.pullGroup() }
            }
        }
        if (!c.loggedIn) {
            item {
                NeonCard(accent = Neon.Cyan, glow = true) {
                    Text(
                        tr("Группа {0}", vm.group.orEmpty()), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Neon.Cyan,
                        style = TextStyle(shadow = Neon.glow(Neon.Cyan, 26f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Войдите в профиль, чтобы видеть объявления старосты, общие ДЗ, одногруппников и вести учёт пропусков."), fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                    GradientButton(tr("Войти или зарегистрироваться"), onClick = onOpenProfile)
                }
            }
            items(
                listOf(
                    Triple("📣", "Объявления", "Переносы пар и новости от старосты") to Neon.Violet,
                    Triple("📝", "Общие ДЗ", "Задания, которые видит вся группа") to Neon.Magenta,
                    Triple("👥", "Одногруппники", "Список группы и контакты старосты") to Neon.Cyan,
                    Triple("📊", "Посещаемость", "Отметки о пропусках и отработках") to Neon.Lime,
                )
            ) { (t, color) ->
                NeonCard(accent = color) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.first, fontSize = 26.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr(t.second), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                            Text(tr(t.third), color = Neon.Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@LazyColumn
        }
        item {
            val badge = if (c.unseenNews() > 0) " •" else ""
            SegmentedTabs(
                listOf(tr("Объявления") + badge to true, tr("ДЗ") to true, tr("Группа") to true, tr("Пропуски") to true),
                tab, { tab = it }, horizontalPadding = 0.dp,
            )
        }
        if (d == null) {
            item {
                if (c.error != null && !c.pulling) NeonCard(accent = Neon.Amber) {
                    Text(c.error.orEmpty(), color = Neon.Amber, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(tr("Повторить"), Neon.Amber) { c.pullGroup() }
                } else Text(tr("Загрузка…"), color = Neon.Muted, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
            }
            return@LazyColumn
        }
        when (tab) {
            0 -> newsTab(vm, d) { sheet = GroupSheet.News }
            1 -> hwTab(vm, d) { sheet = GroupSheet.Share }
            2 -> membersTab(vm, d) { sheet = GroupSheet.Member(it) }
            else -> attTab(vm, d) { sheet = GroupSheet.Att }
        }
    }

    when (val s = sheet) {
        GroupSheet.News -> NewsSheet(vm) { sheet = null }
        GroupSheet.Share -> ShareSheet(vm) { sheet = null }
        GroupSheet.Att -> AttSheet(vm) { sheet = null }
        is GroupSheet.Member -> d?.members?.firstOrNull { it.id == s.id }?.let { MemberSheet(vm, it) { sheet = null } }
        null -> {}
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.newsTab(vm: AppViewModel, d: GroupData, onNew: () -> Unit) {
    val c = vm.cloud
    if (c.isLead) item { GradientButton("+  " + tr("Новое объявление"), onClick = onNew) }
    if (d.news.isEmpty()) item {
        NeonCard(accent = Neon.Violet) {
            Text(tr("Объявлений пока нет"), color = Neon.Violet, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tr("Здесь появятся переносы пар и новости от старосты."), color = Neon.Muted)
        }
    }
    items(d.news, key = { "n" + it.id }) { n ->
        val color = newsColor(n.kind)
        NeonCard(accent = color) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonChip(tr(NEWS_KIND.firstOrNull { it.first == n.kind }?.second ?: "Новость"), color)
                Spacer(Modifier.weight(1f))
                if (n.authorId == c.user?.id || c.user?.role == "starosta") {
                    IconButton(onClick = { c.deleteNews(n.id) }) { Icon(Icons.Filled.Delete, tr("Удалить"), tint = Neon.Dim) }
                }
            }
            if (n.title.isNotBlank()) Text(n.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(n.text, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("${n.author} · ${fmtWhen(n.created)}", color = Neon.Dim, fontSize = 12.sp)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hwTab(vm: AppViewModel, d: GroupData, onNew: () -> Unit) {
    val c = vm.cloud
    val today = LocalDate.now()
    val up = d.homework.filter { it.date >= today }
    val past = d.homework.filter { it.date < today }.reversed().take(15)
    item { GradientButton("+  " + tr("Поделиться ДЗ с группой"), onClick = onNew) }
    if (d.homework.isEmpty()) item {
        NeonCard(accent = Neon.Magenta) {
            Text(tr("Общих ДЗ пока нет"), color = Neon.Magenta, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tr("Поделитесь заданием — его увидит вся группа и сможет добавить себе."), color = Neon.Muted)
        }
    }
    listOf(up to tr("Предстоящие · {0}", up.size), past to tr("Прошедшие")).forEach { (list, title) ->
        if (list.isNotEmpty()) item { SectionTitle(title, color = if (list === up) Neon.Magenta else Neon.Muted) }
        items(list, key = { "h" + it.id }) { h ->
            val have = h.id in c.importedHw
            NeonCard(accent = Neon.Magenta, corner = 18.dp) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(h.subject, color = Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(h.text, fontSize = 15.sp)
                        Text(
                            tr("к паре: {0}", "${h.date.relative(today)}, ${TimeSlot.fmt(h.start)}") + " · " + h.author,
                            color = Neon.Dim, fontSize = 12.sp,
                        )
                    }
                    if (h.authorId == c.user?.id || c.isLead) {
                        IconButton(onClick = { c.deleteGroupHw(h.id) }) { Icon(Icons.Filled.Delete, tr("Удалить"), tint = Neon.Dim) }
                    }
                }
                if (h.date >= today) {
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        if (have) "✓ " + tr("Уже в моих ДЗ") else "+ " + tr("Добавить в мои ДЗ"),
                        if (have) Neon.Lime else Neon.Cyan, enabled = !have,
                    ) { c.takeHomework(h) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.membersTab(vm: AppViewModel, d: GroupData, onOpen: (String) -> Unit) {
    val me = vm.cloud.user?.id
    item { SectionTitle(tr("Участники · {0}", d.members.size)) }
    items(d.members, key = { "m" + it.id }) { m ->
        val rc = roleColor(m.role)
        NeonCard(accent = if (m.role == "member") Neon.Stroke else rc, corner = 18.dp, onClick = { onOpen(m.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.avatar)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name + if (m.id == me) " (" + tr("вы") + ")" else "", fontWeight = FontWeight.Bold)
                    if (m.contact.isNotBlank()) Text(m.contact, color = Neon.Cyan, fontSize = 13.sp)
                }
                if (m.role != "member") NeonChip(tr(ROLE_TITLE[m.role] ?: ""), rc)
            }
        }
    }
}

private fun counts(list: List<AttRecord>) = ATT_STATUS.associate { (k, _) -> k to list.count { it.status == k } }

private fun androidx.compose.foundation.lazy.LazyListScope.attTab(vm: AppViewModel, d: GroupData, onNew: () -> Unit) {
    val c = vm.cloud
    val mine = d.attendance.filter { it.userId == c.user?.id }
    val k = counts(mine)
    item {
        NeonCard(accent = Neon.Lime, glow = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ATT_STATUS.forEach { (s, t) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${k[s]}", fontSize = 30.sp, fontWeight = FontWeight.Black, color = attColor(s))
                        Text(tr(t), color = Neon.Muted, fontSize = 12.sp)
                    }
                }
            }
            if ((k["absent"] ?: 0) > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Осталось отработать: {0}", k["absent"]), color = Neon.Magenta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
    item { GradientButton("+  " + tr("Отметить пропуск"), onClick = onNew) }
    if (c.isLead) item { AllToggle(vm, d) }
    if (!c.isLead || !showAll) {
        if (mine.isEmpty()) item {
            NeonCard(accent = Neon.Lime) {
                Text(tr("Пропусков нет 🎉"), color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(tr("Отмечайте пропуски, чтобы не забыть их отработать."), color = Neon.Muted)
            }
        } else item { SectionTitle(tr("История")) }
        items(mine, key = { "a" + it.id }) { a ->
            val sc = attColor(a.status)
            NeonCard(accent = sc, corner = 18.dp) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(a.subject, fontWeight = FontWeight.Bold)
                        Text(a.date.relative(LocalDate.now()) + if (a.note.isNotBlank()) " · " + a.note else "", color = Neon.Muted, fontSize = 13.sp)
                    }
                    NeonChip(tr(ATT_STATUS.first { it.first == a.status }.second), sc)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (a.status != "done") OutlineButton("✓ " + tr("Отработано"), Neon.Lime, Modifier.weight(1f)) { c.setAttendance(a, "done") }
                    else OutlineButton(tr("Вернуть в пропуски"), Neon.Muted, Modifier.weight(1f)) { c.setAttendance(a, "absent") }
                    IconButton(onClick = { c.deleteAttendance(a) }) { Icon(Icons.Filled.Delete, tr("Удалить"), tint = Neon.Dim) }
                }
            }
        }
    } else {
        items(d.members, key = { "am" + it.id }) { m ->
            val l = d.attendance.filter { it.userId == m.id }
            val mk = counts(l)
            NeonCard(accent = Neon.Stroke, corner = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.avatar, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(m.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    ATT_STATUS.forEach { (s, _) -> Spacer(Modifier.width(4.dp)); NeonChip("${mk[s]}", attColor(s)) }
                }
                l.take(8).forEach { a ->
                    Text(
                        "${a.date.relative(LocalDate.now())} · ${a.subject} · " + tr(ATT_STATUS.first { it.first == a.status }.second),
                        color = Neon.Dim, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** Переключатель «Мои / Вся группа» для старосты и заместителя. */
private var showAll by mutableStateOf(false)

@Composable
private fun AllToggle(vm: AppViewModel, d: GroupData) {
    SegmentedTabs(listOf(tr("Мои") to true, tr("Вся группа") to true), if (showAll) 1 else 0, { showAll = it == 1 }, horizontalPadding = 0.dp)
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
private fun SheetTitle(text: String, color: Color) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, style = TextStyle(shadow = Neon.glow(color, 18f)))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NewsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf("news") }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    FormSheet(onDismiss) {
        SheetTitle(tr("Новое объявление"), Neon.Violet)
        ChoiceChips(NEWS_KIND.map { it.first to tr(it.second) }, kind, { newsColor(it) }) { kind = it }
        FormSpacer()
        NeonField(title, { title = it }, tr("Заголовок"), placeholder = tr("Например: Перенос фармакологии"))
        FormSpacer()
        NeonField(text, { text = it }, tr("Текст"), minLines = 4)
        Spacer(Modifier.height(14.dp))
        GradientButton(tr("Опубликовать"), enabled = text.isNotBlank() && vm.cloud.busy == null, loading = vm.cloud.busy == "news") {
            vm.cloud.addNews(kind, title, text, onDismiss)
        }
    }
}

@Composable
private fun ShareSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val target = subject?.let { vm.nextBySubject(it) }
    FormSheet(onDismiss) {
        SheetTitle(tr("Поделиться ДЗ с группой"), Neon.Magenta)
        Text(tr("Предмет"), color = Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChoiceChips(vm.subjects().map { it to it }, subject) { subject = it }
        if (subject != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                target?.let { tr("К следующей паре: {0}", "${it.date.weekdayShort()}, ${it.date.dayMonth()}, ${it.timeText}") }
                    ?: tr("Будущих пар по предмету нет"),
                color = if (target != null) Neon.Cyan else Neon.Amber, fontSize = 13.sp,
            )
        }
        FormSpacer()
        NeonField(text, { text = it }, tr("Что задали?"), minLines = 3)
        Spacer(Modifier.height(14.dp))
        GradientButton(tr("Отправить группе"), enabled = target != null && text.isNotBlank() && vm.cloud.busy == null, loading = vm.cloud.busy == "ghw") {
            target?.let { vm.cloud.shareHomework(it, text, onDismiss) }
        }
    }
}

@Composable
private fun AttSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val daySubjects = vm.lessonsOn(date).map { it.subject }.distinct()
    var subject by rememberSaveable(dateEpoch) { mutableStateOf(daySubjects.firstOrNull()) }
    var status by rememberSaveable { mutableStateOf("absent") }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    FormSheet(onDismiss) {
        SheetTitle(tr("Отметить пропуск"), Neon.Lime)
        OutlineButton("📅 " + "${date.weekdayShort()}, ${date.dayMonth()}", Neon.Cyan) { picking = true }
        FormSpacer()
        Text(tr("Предмет"), color = Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChoiceChips((daySubjects.ifEmpty { vm.subjects() }).map { it to it }, subject) { subject = it }
        FormSpacer()
        Text(tr("Статус"), color = Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChoiceChips(ATT_STATUS.map { it.first to tr(it.second) }, status, { attColor(it) }) { status = it }
        FormSpacer()
        NeonField(note, { note = it }, tr("Заметка"), placeholder = tr("Например: справка, отработка у преподавателя"))
        Spacer(Modifier.height(14.dp))
        GradientButton(tr("Сохранить"), enabled = subject != null && vm.cloud.busy == null, loading = vm.cloud.busy == "att") {
            vm.cloud.addAttendance(date, subject.orEmpty(), status, note, onDismiss)
        }
    }
    if (picking) NeonDatePickerDialog(tr("Дата"), date, onDismiss = { picking = false }, onConfirm = { dateEpoch = it.toEpochDay(); picking = false })
}

@Composable
private fun MemberSheet(vm: AppViewModel, u: CloudUser, onDismiss: () -> Unit) {
    val c = vm.cloud
    val context = LocalContext.current
    val me = c.user
    val canManage = me?.role == "starosta" && u.id != me.id
    var confirm by remember { mutableStateOf<String?>(null) }
    FormSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.avatar, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(u.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(tr(ROLE_TITLE[u.role] ?: "Участник"), color = Neon.Muted, fontSize = 13.sp)
            }
        }
        FormSpacer()
        if (u.contact.isNotBlank()) {
            Text(tr("Контакт"), color = Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(u.contact, fontSize = 15.sp)
            contactUri(u.contact)?.let { uri ->
                Spacer(Modifier.height(10.dp))
                OutlineButton(tr("Написать"), Neon.Cyan) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        } else Text(tr("Контакт не указан"), color = Neon.Dim)
        if (canManage) {
            SectionTitle(tr("Управление"))
            if (u.role == "deputy") OutlineButton(tr("Снять заместителя"), Neon.Violet, loading = c.busy == "role") { c.setRole(u.id, "member", onDismiss) }
            else OutlineButton(tr("Назначить заместителем"), Neon.Violet, loading = c.busy == "role") { c.setRole(u.id, "deputy", onDismiss) }
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr("Передать роль старосты"), Neon.Magenta) { confirm = "starosta" }
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr("Удалить из группы"), Neon.Muted) { confirm = "kick" }
        }
    }
    when (confirm) {
        "starosta" -> ConfirmDialog(tr("Передать роль старосты"), tr("Передать роль старосты? Вы станете заместителем."), { confirm = null }) {
            confirm = null; c.setRole(u.id, "starosta", onDismiss)
        }
        "kick" -> ConfirmDialog(tr("Удалить из группы"), tr("Удалить участника из группы?"), { confirm = null }) {
            confirm = null; c.removeMember(u.id, onDismiss)
        }
    }
}
