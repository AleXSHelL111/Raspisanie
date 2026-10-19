package com.alexshell.raspisanie.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.ui.theme.Neon

data class NavItem(val title: String, val icon: ImageVector? = null, val badge: Int = 0, val iconRes: Int? = null, val special: Boolean = false)

/** Плавающая неоновая панель навигации в стиле карточек приложения. */
@Composable
fun NeonBottomBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(20.dp, shape, ambientColor = Neon.Violet, spotColor = Neon.Cyan)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Neon.SurfaceHi, Neon.Surface)))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Neon.Cyan.copy(alpha = 0.55f), Neon.Violet.copy(alpha = 0.35f), Neon.Magenta.copy(alpha = 0.55f))
                    ),
                    shape,
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                if (item.special) AiBarLabel(item, Modifier.weight(1f)) { onSelect(i) }
                else BarItem(item, i == selected, Modifier.weight(1f)) { onSelect(i) }
            }
        }
        // Круглая кнопка ИИ выступает над панелью, поэтому рисуется поверх неё, а не внутри.
        val ai = items.indexOfFirst { it.special }
        if (ai >= 0) AiCircle(items[ai], ai == selected, Modifier.align(Alignment.TopCenter)) { onSelect(ai) }
    }
}

@Composable
private fun BarItem(item: NavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Neon.Cyan else Neon.Muted, label = "navColor")
    val glow by animateFloatAsState(if (selected) 1f else 0f, label = "navGlow")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Светящаяся подложка под активной иконкой
            Box(
                Modifier
                    .size(width = 52.dp, height = 32.dp)
                    .alpha(glow)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Neon.Cyan.copy(alpha = 0.28f), Neon.Violet.copy(alpha = 0.22f)))
                    )
                    .border(1.dp, Neon.Cyan.copy(alpha = 0.6f * glow), RoundedCornerShape(16.dp))
            )
            item.icon?.let { Icon(it, contentDescription = item.title, tint = color, modifier = Modifier.size(22.dp)) }
            if (item.badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Neon.Magenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (item.badge > 9) "9+" else item.badge.toString(),
                        color = Neon.OnAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.title,
            color = color,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = if (selected) TextStyle(shadow = Neon.glow(Neon.Cyan, 10f)) else TextStyle.Default,
        )
        Spacer(Modifier.width(1.dp))
    }
}

/** Подпись под центральной кнопкой KGMU AI (сама кнопка — [AiCircle]). */
@Composable
private fun AiBarLabel(item: NavItem, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(35.dp))
        Text(
            item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(brush = Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Magenta))),
        )
    }
}

/** Центральная кнопка KGMU AI: выступающий светящийся круг с градиентом. */
@Composable
private fun AiCircle(item: NavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "ai").animateFloat(
        0.45f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val scale by animateFloatAsState(if (selected) 1.07f else 1f, label = "aiScale")
    Box(
        modifier
            .offset(y = (-20).dp)
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(18.dp * pulse, CircleShape, ambientColor = Neon.Magenta, spotColor = Neon.Violet)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
            .border(3.dp, Neon.Bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        item.iconRes?.let {
            Icon(androidx.compose.ui.res.painterResource(it), contentDescription = item.title, tint = Neon.OnAccent, modifier = Modifier.size(28.dp))
        }
    }
}
