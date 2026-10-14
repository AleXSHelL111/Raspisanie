package com.alexshell.raspisanie.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.ui.components.AVATAR_COLORS
import com.alexshell.raspisanie.ui.components.AVATAR_EMOJI
import com.alexshell.raspisanie.ui.components.Avatar
import com.alexshell.raspisanie.ui.components.AvatarPicker
import com.alexshell.raspisanie.ui.components.FormSpacer
import com.alexshell.raspisanie.ui.components.GradientButton
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonField
import com.alexshell.raspisanie.ui.components.OutlineButton
import com.alexshell.raspisanie.ui.components.SectionTitle
import com.alexshell.raspisanie.ui.relative
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val ROLE_TITLE = mapOf("starosta" to "Староста", "deputy" to "Заместитель", "member" to "Участник")

/** «Сегодня, 19:41» из ISO-времени сервера. */
fun fmtWhen(iso: String?): String {
    val t = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val dt = LocalDateTime.ofInstant(t, ZoneId.systemDefault())
    return "${dt.toLocalDate().relative(LocalDate.now())}, %02d:%02d".format(dt.hour, dt.minute)
}

/** Раздел «Профиль» в настройках: вход, синхронизация, резервная копия. */
@Composable
fun ProfileScreen(vm: AppViewModel, pad: PaddingValues) {
    val c = vm.cloud
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::saveBackupFile)
    }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri?.toString()
    }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = pad.calculateTopPadding() + 12.dp,
            bottom = pad.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!c.loggedIn) {
            item { AuthCard(vm) }
        } else {
            item { ProfileCard(vm) }
            item { SectionTitle(tr("Синхронизация"), color = Neon.Cyan) }
            item {
                NeonCard {
                    Text(
                        tr("ДЗ, правки расписания, настройки и сам файл расписания хранятся в облаке группы. Войдите с тем же именем на другом телефоне или на сайте — и всё перенесётся."),
                        color = Neon.Muted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c.syncAt?.let { tr("Последнее сохранение: {0}", fmtWhen(it)) } ?: tr("Ещё не сохранялось"),
                        fontSize = 13.sp,
                    )
                    FormSpacer()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton("☁↑ " + tr("Сохранить"), Neon.Cyan, Modifier.weight(1f), enabled = c.busy == null, loading = c.busy == "upload") { c.uploadNow() }
                        OutlineButton("☁↓ " + tr("Загрузить"), Neon.Violet, Modifier.weight(1f), enabled = c.busy == null, loading = c.busy == "download") { confirm = "download" }
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Автосохранение после изменений"), Modifier.weight(1f), fontSize = 15.sp)
                        Switch(
                            checked = c.auto, onCheckedChange = { c.toggleAuto() },
                            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnAccent),
                        )
                    }
                }
            }
        }
        item { SectionTitle(tr("Резервная копия"), color = Neon.Lime) }
        item {
            NeonCard(accent = Neon.Lime) {
                Text(tr("Файл со всеми ДЗ, правками и настройками. Его можно открыть и на сайте."), color = Neon.Muted, fontSize = 13.sp)
                FormSpacer()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(tr("Сохранить файл"), Neon.Lime, Modifier.weight(1f)) { saveFile.launch("kgmu-backup-${LocalDate.now()}.json") }
                    OutlineButton(tr("Восстановить"), Neon.Lime, Modifier.weight(1f)) { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                }
            }
        }
        if (c.loggedIn) {
            item { SectionTitle(tr("Аккаунт")) }
            item {
                NeonCard(accent = Neon.Stroke) {
                    OutlineButton(tr("Выйти из профиля"), Neon.Muted) { confirm = "logout" }
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(tr("Удалить профиль"), Neon.Magenta, enabled = c.busy == null) { confirm = "delete" }
                }
            }
        }
        item { SectionTitle(tr("Сервер"), color = Neon.Violet) }
        item { ServerCard(vm) }
    }

    restoreUri?.let { u ->
        ConfirmDialog(tr("Восстановить"), tr("Заменить данные на этом устройстве данными из файла?"), onDismiss = { restoreUri = null }) {
            vm.restoreBackupFile(android.net.Uri.parse(u)); restoreUri = null
        }
    }
    when (confirm) {
        "download" -> ConfirmDialog(tr("Загрузить"), tr("Заменить данные на этом устройстве копией из облака?"), { confirm = null }) { c.download(); confirm = null }
        "logout" -> ConfirmDialog(tr("Выйти из профиля"), tr("Выйти из профиля? Данные на устройстве сохранятся."), { confirm = null }) { c.logout(); confirm = null }
        "delete" -> ConfirmDialog(tr("Удалить профиль"), tr("Удалить профиль и облачную копию навсегда? Данные на устройстве сохранятся."), { confirm = null }) { c.deleteAccount(); confirm = null }
    }
    c.restoreAsk?.let { at ->
        AlertDialog(
            onDismissRequest = { c.answerRestore(false) },
            containerColor = Neon.SurfaceHi,
            title = { Text(tr("Найдена копия в облаке")) },
            text = { Text(tr("Сохранено: {0}. Загрузить ДЗ, правки и настройки на это устройство? Текущие данные будут заменены.", fmtWhen(at))) },
            confirmButton = { TextButton(onClick = { c.answerRestore(true) }) { Text(tr("Загрузить"), color = Neon.Cyan, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { c.answerRestore(false) }) { Text(tr("Нет, оставить эти"), color = Neon.Muted) } },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neon.SurfaceHi,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Да"), color = Neon.Magenta, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена"), color = Neon.Muted) } },
    )
}

@Composable
private fun AuthCard(vm: AppViewModel) {
    val c = vm.cloud
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf(AVATAR_EMOJI.random() + "|" + AVATAR_COLORS[0]) }
    NeonCard(accent = Neon.Violet, glow = true) {
        Text(if (register) tr("Регистрация") else tr("Вход"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Violet)
        Text(tr("Профиль нужен для синхронизации и раздела «Моя группа». Группа: {0}", vm.group.orEmpty()), color = Neon.Muted, fontSize = 13.sp)
        FormSpacer()
        SegmentedTabs(listOf(tr("Вход") to true, tr("Регистрация") to true), if (register) 1 else 0, { register = it == 1 }, horizontalPadding = 0.dp)
        NeonField(name, { name = it }, tr("Имя и фамилия"), placeholder = tr("Как вас видит группа"))
        FormSpacer()
        NeonField(pass, { pass = it }, tr("Пароль"), placeholder = tr("Минимум 4 символа"), password = true)
        if (register) {
            FormSpacer()
            NeonField(contact, { contact = it }, tr("Контакт (необязательно)"), placeholder = tr("@telegram или телефон"))
            FormSpacer()
            Text(tr("Аватар"), color = Neon.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            AvatarPicker(avatar) { avatar = it }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(if (register) tr("Создать профиль") else tr("Войти"), enabled = c.busy == null, loading = c.busy == "auth") {
            val g = vm.group.orEmpty()
            if (register) c.register(g, name, pass, contact, avatar) else c.login(g, name, pass)
        }
        if (!register) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Первый зарегистрированный в группе становится старостой."), color = Neon.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileCard(vm: AppViewModel) {
    val c = vm.cloud
    val u = c.user ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(editing) { mutableStateOf(u.name) }
    var contact by rememberSaveable(editing) { mutableStateOf(u.contact) }
    var avatar by rememberSaveable(editing) { mutableStateOf(u.avatar) }
    var pass by rememberSaveable(editing) { mutableStateOf("") }
    NeonCard(accent = Neon.Violet, glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.avatar, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(u.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(tr("Группа {0}", u.group) + " · " + tr(ROLE_TITLE[u.role] ?: "Участник"), color = Neon.Muted, fontSize = 13.sp)
                if (u.contact.isNotBlank()) Text(u.contact, color = Neon.Cyan, fontSize = 13.sp)
            }
        }
        if (editing) {
            FormSpacer()
            NeonField(name, { name = it }, tr("Имя и фамилия"))
            FormSpacer()
            NeonField(contact, { contact = it }, tr("Контакт (необязательно)"), placeholder = tr("@telegram или телефон"))
            FormSpacer()
            AvatarPicker(avatar) { avatar = it }
            FormSpacer()
            NeonField(pass, { pass = it }, tr("Новый пароль (необязательно)"), password = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(tr("Отмена"), Neon.Muted, Modifier.weight(1f)) { editing = false }
                OutlineButton(tr("Сохранить"), Neon.Cyan, Modifier.weight(1f), enabled = c.busy == null, loading = c.busy == "profile") {
                    c.updateProfile(name, contact, avatar, pass) { editing = false }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            OutlineButton("✎ " + tr("Изменить профиль"), Neon.Violet) { editing = true }
        }
        if (u.group != vm.group) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Профиль привязан к группе {0}, а в расписании выбрана {1}.", u.group, vm.group.orEmpty()), color = Neon.Amber, fontSize = 13.sp)
        }
    }
}

@Composable
fun ServerCard(vm: AppViewModel) {
    val c = vm.cloud
    var url by rememberSaveable { mutableStateOf(c.url) }
    NeonCard(accent = Neon.Violet) {
        Text(
            tr("Адрес веб-приложения Google Apps Script (заканчивается на /exec). Его даёт тот, кто настроил таблицу группы."),
            color = Neon.Muted, fontSize = 13.sp,
        )
        FormSpacer()
        NeonField(url, { url = it }, tr("Адрес сервера"), placeholder = "https://script.google.com/macros/s/…/exec")
        Spacer(Modifier.height(8.dp))
        OutlineButton(tr("Сохранить и проверить"), Neon.Violet, enabled = c.busy == null, loading = c.busy == "ping") { c.setServer(url) }
    }
}
