package com.alexshell.raspisanie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.LessonKind

object Neon {
    val Bg = Color(0xFF07070F)
    val Surface = Color(0xFF0F0F1C)
    val SurfaceHi = Color(0xFF171729)
    val Stroke = Color(0xFF26263D)

    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2E97)
    val Violet = Color(0xFFB026FF)
    val Lime = Color(0xFF39FF88)
    val Amber = Color(0xFFFFC233)

    val Text = Color(0xFFEDEDF7)
    val Muted = Color(0xFF8C8CAA)
    val Dim = Color(0xFF55556E)

    val MainGradient = Brush.linearGradient(listOf(Cyan, Violet, Magenta))

    fun kindColor(kind: LessonKind): Color = when (kind) {
        LessonKind.LECTURE -> Violet
        LessonKind.PRACTICE -> Cyan
        LessonKind.ELECTIVE -> Lime
    }

    fun glow(color: Color, radius: Float = 18f) = Shadow(color = color.copy(alpha = 0.85f), blurRadius = radius)

    /** Цифры одинаковой ширины — таймеры не «прыгают». */
    val Digits = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)
}

private val colors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Color.Black,
    secondary = Neon.Magenta,
    onSecondary = Color.Black,
    tertiary = Neon.Violet,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.SurfaceHi,
    onSurfaceVariant = Neon.Muted,
    surfaceContainer = Neon.Surface,
    surfaceContainerHigh = Neon.SurfaceHi,
    surfaceContainerHighest = Neon.SurfaceHi,
    surfaceContainerLow = Neon.Surface,
    outline = Neon.Stroke,
    outlineVariant = Neon.Stroke,
    error = Neon.Magenta,
)

private val typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 2.sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
