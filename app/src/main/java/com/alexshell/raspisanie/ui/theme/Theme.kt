package com.alexshell.raspisanie.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.LessonKind
import kotlin.math.max
import kotlin.math.min

/** Акцентные цвета темы (значения для тёмного режима; для светлого вычисляются). */
data class Palette(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

object Palettes {
    val all = listOf(
        Palette("neon", "Неон", Color(0xFF00E5FF), Color(0xFFFF2E97), Color(0xFFB98CFF)),
        Palette("synthwave", "Синтвейв", Color(0xFFFF4FD8), Color(0xFFFFB347), Color(0xFF9D8CFF)),
        Palette("sunset", "Закат", Color(0xFFFF8A3D), Color(0xFFFF4D7E), Color(0xFFFFC94D)),
        Palette("matrix", "Матрица", Color(0xFF3DFF7A), Color(0xFF00E0C6), Color(0xFFB6FF3D)),
        Palette("ice", "Лёд", Color(0xFF5CD6FF), Color(0xFF9C8CFF), Color(0xFF6FF0D0)),
        Palette("cyberred", "Кибер-красный", Color(0xFFFF4060), Color(0xFFFF9F1C), Color(0xFFFF7AD9)),
        Palette("gold", "Золото", Color(0xFFFFCF4D), Color(0xFFFF8F3F), Color(0xFFFFA8C5)),
    )

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: all.first()
}

enum class ThemeMode(val title: String) { DARK("Тёмная"), LIGHT("Светлая"), SYSTEM("Как в системе") }

/** Полный набор цветов интерфейса, выведенный из акцента и режима. */
data class Scheme(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val stroke: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val success: Color,
    val warning: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val onAccent: Color,
)

private fun hsl(c: Color): FloatArray {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return floatArrayOf(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return floatArrayOf(h, s, l)
}

/** Тот же оттенок, но темнее и чуть менее насыщенный — читаемо на светлом фоне. */
private fun forLight(c: Color, lightness: Float = 0.40f): Color {
    val v = hsl(c)
    return Color.hsl(v[0].coerceIn(0f, 359.9f), min(v[1], 0.85f), lightness)
}

fun buildScheme(p: Palette, dark: Boolean): Scheme = if (dark) {
    val tint = p.primary
    val bg = lerp(Color(0xFF07070C), tint, 0.05f)
    val text = lerp(Color(0xFFF5F5FA), tint, 0.04f)
    Scheme(
        dark = true,
        bg = bg,
        surface = lerp(Color(0xFF101017), tint, 0.06f),
        surfaceHi = lerp(Color(0xFF181822), tint, 0.08f),
        stroke = lerp(Color(0xFF2A2A36), tint, 0.18f),
        primary = p.primary,
        secondary = p.secondary,
        tertiary = p.tertiary,
        success = lerp(Color(0xFF3DDC97), tint, 0.15f),
        warning = lerp(Color(0xFFFFC14D), tint, 0.15f),
        text = text,
        muted = lerp(text, bg, 0.30f),
        dim = lerp(text, bg, 0.48f),
        onAccent = Color(0xFF0A0A10),
    )
} else {
    val tint = p.primary
    val bg = lerp(Color(0xFFF3F4F8), tint, 0.07f)
    val text = lerp(Color(0xFF14141C), tint, 0.06f)
    val primary = forLight(p.primary)
    Scheme(
        dark = false,
        bg = bg,
        surface = lerp(Color(0xFFFFFFFF), tint, 0.03f),
        surfaceHi = Color(0xFFFFFFFF),
        stroke = lerp(Color(0xFFD5D7E2), tint, 0.18f),
        primary = primary,
        secondary = forLight(p.secondary, 0.45f),
        tertiary = forLight(p.tertiary, 0.42f),
        success = lerp(Color(0xFF12925E), primary, 0.12f),
        warning = lerp(Color(0xFFB4730F), primary, 0.12f),
        text = text,
        muted = lerp(text, bg, 0.36f),
        dim = lerp(text, bg, 0.52f),
        onAccent = Color.White,
    )
}

object Neon {
    /** Выбранный акцент и режим. Меняются в настройках — интерфейс перекрашивается сразу. */
    var palette by mutableStateOf(Palettes.all.first())
    var mode by mutableStateOf(ThemeMode.DARK)
    /** Тёмная ли тема в системе (для режима «Как в системе»). */
    var systemDark by mutableStateOf(true)

    val isDark: Boolean get() = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && systemDark)

    private val schemeState = derivedStateOf { buildScheme(palette, isDark) }
    val scheme: Scheme get() = schemeState.value

    val Bg get() = scheme.bg
    val Surface get() = scheme.surface
    val SurfaceHi get() = scheme.surfaceHi
    val Stroke get() = scheme.stroke

    // Названия исторические: Cyan — основной акцент, Magenta — второй, Violet — третий.
    val Cyan get() = scheme.primary
    val Magenta get() = scheme.secondary
    val Violet get() = scheme.tertiary
    val Lime get() = scheme.success
    val Amber get() = scheme.warning

    val Text get() = scheme.text
    val Muted get() = scheme.muted
    val Dim get() = scheme.dim

    /** Цвет текста/иконок поверх залитых акцентом элементов. */
    val OnAccent get() = scheme.onAccent

    val MainGradient get() = Brush.linearGradient(listOf(Cyan, Violet, Magenta))

    fun kindColor(kind: LessonKind): Color = when (kind) {
        LessonKind.LECTURE -> Violet
        LessonKind.PRACTICE -> Cyan
        LessonKind.ELECTIVE -> Magenta
    }

    /** Неоновое свечение текста; в светлой теме — мягкое. */
    fun glow(color: Color, radius: Float = 18f) =
        if (isDark) Shadow(color = color.copy(alpha = 0.85f), blurRadius = radius)
        else Shadow(color = color.copy(alpha = 0.22f), blurRadius = radius * 0.6f)

    /** Цифры одинаковой ширины — таймеры не «прыгают». */
    val Digits = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)
}

private fun colors() = if (Neon.isDark) darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.OnAccent,
    secondary = Neon.Magenta,
    onSecondary = Neon.OnAccent,
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
    inverseSurface = Neon.Text,
    inverseOnSurface = Neon.Bg,
) else lightColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.OnAccent,
    secondary = Neon.Magenta,
    onSecondary = Neon.OnAccent,
    tertiary = Neon.Violet,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.Bg,
    onSurfaceVariant = Neon.Muted,
    surfaceContainer = Neon.Surface,
    surfaceContainerHigh = Neon.SurfaceHi,
    surfaceContainerHighest = Neon.SurfaceHi,
    surfaceContainerLow = Neon.Surface,
    outline = Neon.Stroke,
    outlineVariant = Neon.Stroke,
    error = Neon.Magenta,
    inverseSurface = Neon.Text,
    inverseOnSurface = Neon.Bg,
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
        // явно задаём цвет текста по умолчанию.
        CompositionLocalProvider(LocalContentColor provides Neon.Text, content = content)
    }
}
