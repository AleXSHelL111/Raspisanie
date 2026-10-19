package com.alexshell.raspisanie.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.ai.Assistant
import com.alexshell.raspisanie.ui.I18n
import com.alexshell.raspisanie.ui.components.AiOrb
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr

/** Вкладка KGMU AI: чат с помощником. */
@Composable
fun AiScreen(vm: AppViewModel, padding: PaddingValues) {
    val chat = vm.ai
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // После отправки прячем клавиатуру.
    val send = { text: String -> if (!chat.busy && text.isNotBlank()) { chat.send(text); input = ""; keyboard?.hide(); focus.clearFocus() } }
    val onLink = { target: String -> chat.openRequest = target }

    // Новое сообщение, «размышление» или печать ответа — прокручиваем вниз.
    val rows = chat.messages.size + (if (chat.thinking != null || chat.typing != null) 1 else 0)
    LaunchedEffect(rows, chat.typing != null) { if (rows > 0) list.animateScrollToItem(rows - 1) }
    LaunchedEffect(chat.typed / 40) { if (chat.typing != null && rows > 0) list.scrollToItem(rows - 1, Int.MAX_VALUE / 2) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = padding.calculateBottomPadding() + 14.dp)
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        // Заголовок
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AiAvatar(44.dp, spinning = true)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "KGMU AI", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
                    style = TextStyle(brush = Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta))),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).shadow(6.dp, CircleShape, ambientColor = Neon.Lime, spotColor = Neon.Lime).clip(CircleShape).background(Neon.Lime))
                    Spacer(Modifier.size(6.dp))
                    Text(tr("онлайн · бета"), color = Neon.Muted, fontSize = 12.sp)
                }
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).border(1.dp, Neon.Cyan.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = chat.messages.isNotEmpty() && !chat.busy) { confirmClear = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Delete, tr("Очистить чат"), tint = Neon.Cyan, modifier = Modifier.size(20.dp)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Stroke.copy(alpha = 0.6f)))

        // Сообщения
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows == 0) {
                EmptyChat(vm)
            } else {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(chat.messages.size) { i ->
                        val m = chat.messages[i]
                        if (m.user) UserBubble(m.text) else BotBubble { Text(rich(m.text, onLink = onLink), color = Neon.Text, fontSize = 15.sp, lineHeight = 21.sp) }
                    }
                    if (chat.thinking != null) item { BotBubble(spinning = true) { Thinking(tr(chat.thinking.orEmpty())) } }
                    chat.typing?.let { full -> item { BotBubble { Text(rich(full, chat.typed, caret = true, onLink = onLink), color = Neon.Text, fontSize = 15.sp, lineHeight = 21.sp) } } }
                }
            }
        }

        // Подсказки и поле ввода
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        ) {
            items(Assistant.SUGGEST) { s ->
                Text(
                    s, color = Neon.Cyan, fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Neon.SurfaceHi)
                        .border(1.dp, Neon.Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { send(s) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Neon.Surface)
                .border(1.dp, Brush.horizontalGradient(listOf(Neon.Cyan.copy(alpha = 0.7f), Neon.Violet.copy(alpha = 0.5f), Neon.Magenta.copy(alpha = 0.7f))), shape)
                .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f).heightIn(min = 42.dp).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (input.isEmpty()) Text(tr("Спросите что-нибудь…"), color = Neon.Dim, fontSize = 16.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = Neon.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(Neon.Cyan),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .alpha(if (input.isBlank() || chat.busy) 0.45f else 1f)
                    .shadow(10.dp, CircleShape, ambientColor = Neon.Violet, spotColor = Neon.Violet)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
                    .clickable { send(input) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Отправить"), tint = Neon.OnAccent, modifier = Modifier.size(20.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Neon.SurfaceHi,
            title = { Text(tr("Очистить чат?"), color = Neon.Text) },
            confirmButton = { TextButton({ chat.clear(); confirmClear = false }) { Text(tr("Очистить"), color = Neon.Magenta) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text(tr("Отмена"), color = Neon.Muted) } },
        )
    }
}

@Composable
private fun EmptyChat(vm: AppViewModel) {
    val name = vm.cloud.user?.fullName?.substringBefore(' ').orEmpty()
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AiOrb(150.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            if (name.isNotEmpty()) tr("Привет, {0}! Я KGMU AI", name) else tr("Привет! Я KGMU AI"),
            fontSize = 22.sp, fontWeight = FontWeight.Black, color = Neon.Text, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Neon.glow(Neon.Violet, 18f)),
        )
        Spacer(Modifier.height(6.dp))
        Text(tr("Спросите о парах, домашке или о приложении — отвечу за секунду"), color = Neon.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (I18n.lang != "ru") {
            Spacer(Modifier.height(8.dp))
            Text(tr("Помощник пока понимает только русский язык"), color = Neon.Dim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text, color = Neon.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BotBubble(spinning: Boolean = false, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 28.dp), verticalAlignment = Alignment.Bottom) {
        AiAvatar(30.dp, spinning = spinning)
        Spacer(Modifier.size(8.dp))
        val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
        Box(
            Modifier
                .clip(shape)
                .background(Neon.SurfaceHi)
                .border(1.dp, Neon.Stroke, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { content() }
    }
}

/** Маленький значок бота; во время «размышления» вокруг него крутится дуга. */
@Composable
private fun AiAvatar(size: Dp, spinning: Boolean) {
    val angle by rememberInfiniteTransition(label = "ava").animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (size > 40.dp) 2400 else 900, easing = LinearEasing)), label = "a",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (spinning) {
            val c1 = Neon.Cyan; val c2 = Neon.Magenta
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                val w = 2.dp.toPx()
                drawArc(
                    Brush.sweepGradient(listOf(c1, c2, c1.copy(alpha = 0f)), center = Offset(this.size.width / 2, this.size.height / 2)),
                    0f, 270f, false, style = Stroke(w, cap = StrokeCap.Round),
                    topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
                )
            }
        }
        val inner = if (spinning) size * 0.74f else size
        Box(
            Modifier
                .size(inner)
                .shadow(10.dp, CircleShape, ambientColor = Neon.Violet, spotColor = Neon.Violet)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta))),
            contentAlignment = Alignment.Center,
        ) { Text("AI", color = Neon.OnAccent, fontWeight = FontWeight.Black, fontSize = (inner.value * 0.36f).sp) }
    }
}

/** «Думаю…» с переливом и прыгающими точками. */
@Composable
private fun Thinking(label: String) {
    val t = rememberInfiniteTransition(label = "think")
    val shift by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Neon.Muted, Neon.Cyan, Neon.Muted),
                    start = Offset(shift * 300f - 150f, 0f), end = Offset(shift * 300f + 150f, 0f),
                ),
            ),
        )
        Spacer(Modifier.size(8.dp))
        listOf(Neon.Cyan, Neon.Violet, Neon.Magenta).forEachIndexed { i, c ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1000, delayMillis = 0, easing = LinearEasing), initialStartOffset = androidx.compose.animation.core.StartOffset(i * 150)),
                label = "dot$i",
            )
            // 0..0.4 — вверх, 0.4..0.8 — вниз, дальше пауза.
            val lift = when { y < 0.4f -> y / 0.4f; y < 0.8f -> (0.8f - y) / 0.4f; else -> 0f }
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .offset(y = (-5 * lift).dp)
                    .size(6.dp)
                    .alpha(0.35f + 0.65f * lift)
                    .clip(CircleShape)
                    .background(c),
            )
        }
    }
}

/** Текст ответа с **жирным** и _(курсивом)_; [limit] — сколько символов уже «напечатано». */
@Composable
private fun rich(text: String, limit: Int = Int.MAX_VALUE, caret: Boolean = false, onLink: (String) -> Unit = {}): AnnotatedString {
    val accent = Neon.Cyan
    val muted = Neon.Muted
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink",
    )
    return buildAnnotatedString {
        var left = limit
        for (p in Assistant.parts(text)) {
            if (left <= 0) break
            val shown = p.text.take(left)
            left -= p.text.length
            when {
                p.link != null -> {
                    val target = p.link
                    val style = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Clickable(target, style) { onLink(target) }) { append(shown) }
                }
                p.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(shown) }
                p.italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = muted)) { append(shown) }
                else -> append(shown)
            }
        }
        if (caret) withStyle(SpanStyle(color = accent.copy(alpha = blink))) { append(" ▍") }
    }
}
