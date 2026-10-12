package com.alexshell.raspisanie.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.ui.theme.Neon

/** Тёмный фон с медленно «дышащими» неоновыми пятнами. */
@Composable
fun NeonBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(
                        listOf(Neon.Violet.copy(alpha = if (Neon.isDark) 0.24f else 0.20f), Color.Transparent),
                        center = Offset(w * (0.15f + 0.2f * shift), h * 0.12f),
                        radius = w * 0.8f,
                    ),
                    radius = w * 0.8f,
                    center = Offset(w * (0.15f + 0.2f * shift), h * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Neon.Cyan.copy(alpha = if (Neon.isDark) 0.18f else 0.16f), Color.Transparent),
                        center = Offset(w * (0.95f - 0.25f * shift), h * 0.55f),
                        radius = w * 0.75f,
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * (0.95f - 0.25f * shift), h * 0.55f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Neon.Magenta.copy(alpha = if (Neon.isDark) 0.15f else 0.12f), Color.Transparent),
                        center = Offset(w * 0.2f, h * (0.95f - 0.1f * shift)),
                        radius = w * 0.7f,
                    ),
                    radius = w * 0.7f,
                    center = Offset(w * 0.2f, h * (0.95f - 0.1f * shift)),
                )
            },
        content = content,
    )
}

/** Карточка из «жидкого стекла»: полупрозрачная, с бликом сверху и светящейся кромкой. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color = Neon.Cyan,
    glow: Boolean = false,
    corner: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            // Все плитки — на всю ширину, независимо от длины текста.
            .fillMaxWidth()
            .then(
                if (glow) Modifier.shadow(20.dp, shape, ambientColor = accent, spotColor = accent)
                else if (!Neon.isDark) Modifier.shadow(6.dp, shape, ambientColor = accent.copy(alpha = 0.4f), spotColor = accent.copy(alpha = 0.25f))
                else Modifier
            )
            .clip(shape)
            .background(Neon.GlassFill)
            .glassHighlight(accent)
            .border(if (glow) 1.5.dp else 1.dp, Neon.glassEdge(accent, strong = glow), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Neon.Text) { content() }
    }
}

/** Блик стекла: мягкое пятно света в левом верхнем углу и цветной отсвет снизу справа. */
fun Modifier.glassHighlight(accent: Color): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = if (Neon.isDark) 0.10f else 0.55f), Color.Transparent),
            center = Offset(size.width * 0.15f, 0f),
            radius = size.width * 0.6f,
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(accent.copy(alpha = if (Neon.isDark) 0.10f else 0.07f), Color.Transparent),
            center = Offset(size.width, size.height),
            radius = size.width * 0.7f,
        )
    )
}

@Composable
fun NeonChip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (filled) Neon.OnAccent else color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Неоновая полоса прогресса. */
@Composable
fun NeonProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Neon.Cyan) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Neon.Stroke)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .shadow(8.dp, RoundedCornerShape(50), ambientColor = color, spotColor = color)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color, Neon.Magenta)))
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Neon.Muted) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
        color = color,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** Круглая неоновая кнопка с иконкой. */
@Composable
fun NeonIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    color: Color = Neon.Cyan,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(46.dp)
            .shadow(12.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = color, spotColor = color)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Neon.GlassFill)
            .glassHighlight(color)
            .border(1.dp, Neon.glassEdge(color, strong = true), androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(22.dp))
    }
}
