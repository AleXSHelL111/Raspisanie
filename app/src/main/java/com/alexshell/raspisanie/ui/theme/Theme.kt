package com.alexshell.raspisanie.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.LessonKind

/** Цветовая тема приложения. */
data class Palette(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val success: Color,
    val warning: Color,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val stroke: Color,
)

object Palettes {
    val all = listOf(
        Palette(
            "neon", "Неон",
            primary = Color(0xFF00E5FF), secondary = Color(0xFFFF2E97), tertiary = Color(0xFFC77DFF),
            success = Color(0xFF39FF88), warning = Color(0xFFFFC233),
            bg = Color(0xFF07070F), surface = Color(0xFF0F0F1C), surfaceHi = Color(0xFF171729), stroke = Color(0xFF26263D),
        ),
        Palette(
            "synthwave", "Синтвейв",
            primary = Color(0xFFFF4FD8), secondary = Color(0xFFFFB347), tertiary = Color(0xFF9D8CFF),
            success = Color(0xFF5CFFC8), warning = Color(0xFFFFD166),
            bg = Color(0xFF0D0618), surface = Color(0xFF170C26), surfaceHi = Color(0xFF221236), stroke = Color(0xFF3A2350),
        ),
        Palette(
            "matrix", "Матрица",
            primary = Color(0xFF39FF14), secondary = Color(0xFF00FFC6), tertiary = Color(0xFF7DF9FF),
            success = Color(0xFFC6FF00), warning = Color(0xFFFFE14D),
            bg = Color(0xFF030A05), surface = Color(0xFF08140B), surfaceHi = Color(0xFF0E1F12), stroke = Color(0xFF1C3523),
        ),
        Palette(
            "sunset", "Закат",
            primary = Color(0xFFFF8A3D), secondary = Color(0xFFFF3D7F), tertiary = Color(0xFFFFD25E),
            success = Color(0xFF5CFFB0), warning = Color(0xFFFFE066),
            bg = Color(0xFF120607), surface = Color(0xFF1C0C0E), surfaceHi = Color(0xFF2A1316), stroke = Color(0xFF45222A),
        ),
        Palette(
            "ice", "Лёд",
            primary = Color(0xFF6FE3FF), secondary = Color(0xFFA68BFF), tertiary = Color(0xFFB9F3FF),
            success = Color(0xFF5CFFD1), warning = Color(0xFFFFD166),
            bg = Color(0xFF050A12), surface = Color(0xFF0B1420), surfaceHi = Color(0xFF12202F), stroke = Color(0xFF22364A),
        ),
        Palette(
            "cyberred", "Кибер-красный",
            primary = Color(0xFFFF3B5C), secondary = Color(0xFFFF9F1C), tertiary = Color(0xFFFF7AD9),
            success = Color(0xFF4DFF9A), warning = Color(0xFFFFD23F),
            bg = Color(0xFF0E0405), surface = Color(0xFF1A0A0C), surfaceHi = Color(0xFF261014), stroke = Color(0xFF41202A),
        ),
        Palette(
            "gold", "Золото",
            primary = Color(0xFFFFD54F), secondary = Color(0xFFFF8F3F), tertiary = Color(0xFFFFF59D),
            success = Color(0xFF7CFFB2), warning = Color(0xFFFFB74D),
            bg = Color(0xFF0B0904), surface = Color(0xFF16120A), surfaceHi = Color(0xFF221C10), stroke = Color(0xFF3A3020),
        ),
    )

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: all.first()
}

object Neon {
    /** Текущая тема; смена мгновенно перекрашивает интерфейс. */
    var palette by mutableStateOf(Palettes.all.first())

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val SurfaceHi get() = palette.surfaceHi
    val Stroke get() = palette.stroke

    // Названия исторические: Cyan — основной акцент, Magenta — второй, Violet — третий.
    val Cyan get() = palette.primary
    val Magenta get() = palette.secondary
    val Violet get() = palette.tertiary
    val Lime get() = palette.success
    val Amber get() = palette.warning

    val Text = Color(0xFFF4F4FF)
    val Muted = Color(0xFFB4B4D0)
    val Dim = Color(0xFF8A8AA6)

    val MainGradient get() = Brush.linearGradient(listOf(Cyan, Violet, Magenta))

    fun kindColor(kind: LessonKind): Color = when (kind) {
        LessonKind.LECTURE -> Violet
        LessonKind.PRACTICE -> Cyan
        LessonKind.ELECTIVE -> Lime
    }

    fun glow(color: Color, radius: Float = 18f) = Shadow(color = color.copy(alpha = 0.85f), blurRadius = radius)

    /** Цифры одинаковой ширины — таймеры не «прыгают». */
    val Digits = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)
}

private fun colors() = darkColorScheme(
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
    MaterialTheme(colorScheme = colors(), typography = typography) {
        // Экраны рисуются поверх собственного фона, а не внутри Surface, поэтому
        // явно задаём светлый цвет текста по умолчанию — иначе он был бы чёрным.
        CompositionLocalProvider(LocalContentColor provides Neon.Text, content = content)
    }
}
