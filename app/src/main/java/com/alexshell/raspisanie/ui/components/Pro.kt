package com.alexshell.raspisanie.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexshell.raspisanie.R
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.tr

const val TELEGRAM_LINK = "https://t.me/kgmuraspisanie"

/** Кнопка «PRO version» по центру верхней строки главного экрана. */
@Composable
fun ProButton(onClick: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "pro").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(36.dp)
            .shadow(14.dp, shape, ambientColor = Neon.Violet.copy(alpha = glow), spotColor = Neon.Magenta.copy(alpha = glow))
            .clip(shape)
            .background(Neon.Surface)
            .border(1.5.dp, Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Black, color = Neon.Magenta, letterSpacing = 1.sp)) { append("PRO") }
                append(" version")
            },
            color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Неоновый «ИИ-шар»: три вращающиеся дуги и пульсирующее ядро. */
@Composable
fun AiOrb(size: androidx.compose.ui.unit.Dp = 190.dp) {
    val k = size / 190.dp
    val t = rememberInfiniteTransition(label = "orb")
    val a1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "a1")
    val a2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "a2")
    val a3 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "a3")
    val pulse by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val c1 = Neon.Cyan; val c2 = Neon.Magenta; val c3 = Neon.Violet
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 4.dp.toPx()
            fun arc(angle: Float, inset: Float, color: Color, sweep: Float) = rotate(angle) {
                val glowStroke = Stroke(w * 3, cap = StrokeCap.Round)
                val stroke = Stroke(w, cap = StrokeCap.Round)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                val sz = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(color.copy(alpha = 0.25f), 0f, sweep, false, tl, sz, style = glowStroke)
                drawArc(color, 0f, sweep, false, tl, sz, style = stroke)
                drawArc(color, 180f, sweep * 0.6f, false, tl, sz, style = stroke)
            }
            arc(a1, w * 2, c1, 110f)
            arc(a2, 22.dp.toPx() * k, c2, 90f)
            arc(a3, 42.dp.toPx() * k, c3, 70f)
        }
        Box(
            Modifier
                .size(74.dp * k)
                .scale(pulse)
                .shadow(30.dp, CircleShape, ambientColor = c3, spotColor = c2)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c1, c3, c2))),
            contentAlignment = Alignment.Center,
        ) { Text("AI", color = Neon.OnAccent, fontSize = 24.sp * k, fontWeight = FontWeight.Black) }
    }
}

/** Окно PRO: бета KGMU AI и ссылка на соцсети. */
@Composable
fun ProDialog(onOpenAi: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(min = 560.dp)
                .shadow(30.dp, shape, ambientColor = Neon.Violet, spotColor = Neon.Violet)
                .clip(shape)
                .background(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.22f), Neon.SurfaceHi, Neon.Surface), radius = 900f))
                .border(1.dp, Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)), shape)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(36.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NeonChip(tr("Бета-версия"), Neon.Magenta, filled = true) }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Neon.Muted, fontSize = 20.sp) }
            }
            Spacer(Modifier.height(28.dp))
            AiOrb()
            Spacer(Modifier.height(24.dp))
            Text(
                tr("KGMU AI уже здесь"), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Neon.Text, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Neon.glow(Neon.Violet, 24f)),
            )
            Spacer(Modifier.height(6.dp))
            Text(tr("для расписания и не только😉"), fontSize = 17.sp, color = Neon.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.width(260.dp)) { GradientButton("✨ " + tr("Открыть чат с ИИ")) { onDismiss(); onOpenAi() } }
            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(36.dp))
            Text(tr("Версия {0}", version) + " · Created by AleXSHelL", color = Neon.Dim, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(tr("Подпишись на соцсети"), color = Neon.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            val tgShape = RoundedCornerShape(50)
            Row(
                Modifier
                    .shadow(14.dp, tgShape, ambientColor = Color(0xFF229ED9), spotColor = Color(0xFF229ED9))
                    .clip(tgShape)
                    .background(Color(0xFF229ED9))
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_LINK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_telegram), null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Telegram", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
