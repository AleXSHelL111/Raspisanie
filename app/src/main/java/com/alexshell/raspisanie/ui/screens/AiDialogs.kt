package com.alexshell.raspisanie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.ai.LocalAi
import com.alexshell.raspisanie.ui.components.GradientButton
import com.alexshell.raspisanie.ui.components.OutlineButton
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun gb(bytes: Long) = "%.1f".format(bytes / 1_073_741_824.0).replace('.', ',') + " " + tr("ГБ")

/** Окно в неоновом стиле поверх экрана. */
@Composable
private fun NeonDialog(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(shape)
                .background(Neon.Surface)
                .border(1.dp, Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)), shape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Neon.Text, modifier = Modifier.weight(1f))
                Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("✕", color = Neon.Muted, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Выбор и загрузка локальной нейросети. */
@Composable
fun ModelsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ai = vm.localAi
    NeonDialog(onDismiss, "✨ " + tr("Нейросеть на устройстве")) {
        Text(
            tr("Скачайте модель Gemma 4 — и KGMU AI будет отвечать как настоящий ИИ: объяснять темы, помогать с учёбой и следовать вашим рекомендациям из профиля. Работает без интернета, переписка не покидает телефон."),
            color = Neon.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(tr("Память телефона: {0} ГБ · свободно: {1}", ai.ramGb, gb(ai.freeSpaceBytes())), color = Neon.Dim, fontSize = 12.sp)

        // Кто отвечает: простой бот или скачанная модель.
        Spacer(Modifier.height(12.dp))
        Choice(
            selected = ai.selectedId == null,
            title = tr("Простой бот"),
            subtitle = tr("Мгновенно отвечает про пары и ДЗ, без нейросети"),
        ) { ai.setSelected(null) }

        LocalAi.MODELS.forEach { m ->
            Spacer(Modifier.height(10.dp))
            ModelCard(ai, m)
        }

        Spacer(Modifier.height(12.dp))
        val status = when (ai.engineState) {
            LocalAi.EngineState.LOADING -> tr("Нейросеть запускается…")
            LocalAi.EngineState.READY -> tr("Нейросеть работает ({0})", ai.engineBackend.orEmpty())
            LocalAi.EngineState.ERROR -> tr("Не удалось запустить: {0}", ai.engineError.orEmpty())
            LocalAi.EngineState.IDLE -> if (ai.usable) tr("Запустится при первом вопросе — это займёт до 10–20 секунд") else ""
        }
        if (status.isNotEmpty()) Text(status, color = if (ai.engineState == LocalAi.EngineState.ERROR) Neon.Magenta else Neon.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Скачивайте по Wi-Fi. Действия (добавить ДЗ, сменить тему) и точные данные о парах по-прежнему выполняет бот — нейросеть только формулирует ответ. Модели: Google Gemma 4 (Apache 2.0), движок LiteRT-LM."),
            color = Neon.Dim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Neon.Cyan.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) Neon.Cyan else Neon.Stroke, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Neon.Cyan else Neon.Dim, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Neon.Cyan)) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Neon.Text)
            Text(subtitle, color = Neon.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModelCard(ai: LocalAi, m: LocalAi.Model) {
    val d = ai.downloads[m.id] ?: LocalAi.Download.None
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, Neon.Stroke, shape).padding(12.dp)) {
        if (d == LocalAi.Download.Ready) {
            Choice(ai.selectedId == m.id, m.name, tr("Скачана · {0}", gb(ai.fileOf(m).length()))) { ai.setSelected(m.id) }
        } else {
            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Neon.Text)
            Text(tr("Размер: {0} · нужно от {1} ГБ памяти", gb(m.sizeBytes), m.minRamGb), color = Neon.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(tr(m.description), color = Neon.Dim, fontSize = 12.sp)
        if (ai.ramGb < m.minRamGb) {
            Spacer(Modifier.height(4.dp))
            Text("⚠️ " + tr("На вашем телефоне {0} ГБ памяти — модель может работать очень медленно или не запуститься.", ai.ramGb), color = Neon.Amber, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        when (d) {
            is LocalAi.Download.Running -> {
                val p = if (d.total > 0) d.done.toFloat() / d.total else 0f
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Neon.Cyan, trackColor = Neon.SurfaceHi,
                )
                Spacer(Modifier.height(4.dp))
                Text(tr("Скачано {0} из {1}", gb(d.done), gb(d.total)), color = Neon.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                OutlineButton(tr("Отменить загрузку"), Neon.Muted) { ai.cancel(m) }
            }
            LocalAi.Download.Ready -> OutlineButton(tr("Удалить модель"), Neon.Magenta) { ai.delete(m) }
            else -> {
                if (d is LocalAi.Download.Failed) {
                    Text("❌ " + tr(d.reason), color = Neon.Magenta, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                val enoughSpace = ai.freeSpaceBytes() > m.sizeBytes + 300_000_000L
                GradientButton(tr("Скачать ({0})", gb(m.sizeBytes)), enabled = enoughSpace) { ai.download(m) }
                if (!enoughSpace) Text(tr("Недостаточно свободного места на телефоне"), color = Neon.Magenta, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Список сохранённых чатов. */
@Composable
fun ChatsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val chat = vm.ai
    val fmt = DateTimeFormatter.ofPattern("d.MM, HH:mm")
    NeonDialog(onDismiss, tr("Мои чаты")) {
        Text(tr("Чаты хранятся только на этом телефоне."), color = Neon.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        GradientButton("+ " + tr("Новый чат"), enabled = !chat.busy) { chat.newChat(); onDismiss() }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chat.chats.filter { it.messages.isNotEmpty() || it.id == chat.currentId }.forEach { c ->
                val selected = c.id == chat.currentId
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (selected) Neon.Cyan.copy(alpha = 0.10f) else Neon.SurfaceHi)
                        .border(1.dp, if (selected) Neon.Cyan else Neon.Stroke, shape)
                        .clickable(enabled = !chat.busy) { chat.select(c.id); onDismiss() }
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title.ifBlank { tr("Новый чат") }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Neon.Text, maxLines = 1)
                        Text(
                            Instant.ofEpochMilli(c.updated).atZone(ZoneId.systemDefault()).format(fmt) + " · " + tr("сообщений: {0}", c.messages.size),
                            color = Neon.Dim, fontSize = 12.sp,
                        )
                    }
                    if (c.messages.isNotEmpty()) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !chat.busy) { chat.delete(c.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Delete, tr("Удалить"), tint = Neon.Muted, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
