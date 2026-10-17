package com.alexshell.raspisanie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.ui.theme.Neon

val AVATAR_EMOJI = listOf("🦊", "🐼", "🐱", "🐶", "🦁", "🐸", "🐧", "🦉", "🐙", "🦄", "🐝", "🐳", "🌸", "⚡", "🔥", "🩺", "💊", "🧠", "🫀", "🧬")
val AVATAR_COLORS = listOf("#00E5FF", "#FF2E97", "#B98CFF", "#3DFF7A", "#FFCF4D", "#FF8A3D", "#5CD6FF", "#FF4060")

/** Аватар хранится строкой «эмодзи|#цвет» — так же, как на сайте. */
fun avatarParts(a: String?): Pair<String, String> {
    val parts = a.orEmpty().split('|')
    return (parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "🙂") to (parts.getOrNull(1)?.takeIf { it.startsWith("#") } ?: AVATAR_COLORS[0])
}

fun hexColor(h: String): Color = runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrDefault(Neon.Cyan)

@Composable
fun Avatar(avatar: String?, size: Dp = 44.dp) {
    val (emoji, hex) = avatarParts(avatar)
    val c = hexColor(hex)
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = c, spotColor = c)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.15f), Neon.Surface)))
            .border(2.dp, c, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.5f).sp) }
}

@Composable
fun AvatarPicker(value: String, onChange: (String) -> Unit) {
    val (emoji, hex) = avatarParts(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AVATAR_EMOJI.chunked(10).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { e ->
                    val sel = e == emoji
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Neon.Cyan.copy(alpha = 0.18f) else Color.Transparent)
                            .border(1.dp, if (sel) Neon.Cyan else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onChange("$e|$hex") },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 19.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            AVATAR_COLORS.forEach { h ->
                val sel = h == hex
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(hexColor(h))
                        .border(if (sel) 3.dp else 0.dp, if (sel) Neon.Text else Color.Transparent, CircleShape)
                        .clickable { onChange("$emoji|$h") }
                )
            }
        }
    }
}

@Composable
fun NeonField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    number: Boolean = false,
    username: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Neon.Dim) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        prefix = if (username) ({ Text("@", color = Neon.Muted) }) else null,
        keyboardOptions = KeyboardOptions(
            capitalization = if (username || password) androidx.compose.ui.text.input.KeyboardCapitalization.None
            else androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                username -> KeyboardType.Email
                else -> KeyboardType.Text
            },
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Neon.Cyan,
            unfocusedBorderColor = Neon.Stroke,
            cursorColor = Neon.Cyan,
            focusedLabelColor = Neon.Cyan,
            unfocusedLabelColor = Neon.Muted,
            focusedTextColor = Neon.Text,
            unfocusedTextColor = Neon.Text,
        ),
    )
}

/** Главная кнопка с неоновым градиентом. */
@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (enabled) Modifier.shadow(12.dp, shape, ambientColor = Neon.Cyan, spotColor = Neon.Violet) else Modifier)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
            .then(if (!enabled) Modifier.background(Neon.Bg.copy(alpha = 0.5f)) else Modifier)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = Neon.OnAccent, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        else Text(text, color = Neon.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun OutlineButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) color else color.copy(alpha = 0.4f)),
    ) {
        if (loading) CircularProgressIndicator(color = color, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(text, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Горизонтальный ряд чипов выбора. */
@Composable
fun <T> ChoiceChips(items: List<Pair<T, String>>, selected: T?, color: (T) -> Color = { Neon.Cyan }, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (v, label) ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(v) },
                label = { Text(if (label.length > 28) label.take(26) + "…" else label) },
                colors = chipColors(color(v)),
            )
        }
    }
}

@Composable
fun FormSpacer() = Spacer(Modifier.height(10.dp))

/** Выпадающий список выбора (например, предмета) в неоновом стиле. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NeonDropdown(
    label: String,
    value: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = Neon.Dim) } },
            singleLine = true,
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon.Cyan, unfocusedBorderColor = Neon.Stroke,
                focusedLabelColor = Neon.Cyan, unfocusedLabelColor = Neon.Muted,
                focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text,
            ),
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Neon.SurfaceHi,
        ) {
            options.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(o, color = if (o == value) Neon.Cyan else Neon.Text, fontWeight = if (o == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}

/** Поле ввода с выпадающими подсказками: можно выбрать из списка или вписать своё. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NeonComboField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shown = if (value.isBlank() || value in options) options else options.filter { it.contains(value.trim(), ignoreCase = true) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open && shown.isNotEmpty(), onExpandedChange = { open = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); open = true },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = Neon.Dim) } },
            singleLine = true,
            trailingIcon = { if (options.isNotEmpty()) androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon.Cyan, unfocusedBorderColor = Neon.Stroke,
                focusedLabelColor = Neon.Cyan, unfocusedLabelColor = Neon.Muted,
                focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, cursorColor = Neon.Cyan,
            ),
        )
        ExposedDropdownMenu(
            expanded = open && shown.isNotEmpty(),
            onDismissRequest = { open = false },
            containerColor = Neon.SurfaceHi,
        ) {
            shown.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(o, color = if (o == value) Neon.Cyan else Neon.Text) },
                    onClick = { onChange(o); open = false },
                )
            }
        }
    }
}
