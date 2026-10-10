package com.alexshell.raspisanie.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.ui.theme.Neon

@Composable
fun HomeworkRow(
    hw: Homework,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    headerColor: Color = Neon.Cyan,
    footer: String? = null,
    footerColor: Color = Neon.Muted,
    onClick: (() -> Unit)? = null,
) {
    NeonCard(
        modifier = modifier.fillMaxWidth(),
        accent = if (hw.done) Neon.Lime else Neon.Magenta,
        corner = 18.dp,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = hw.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Neon.Lime,
                    uncheckedColor = Neon.Magenta,
                    checkmarkColor = Color.Black,
                ),
            )
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                header?.let {
                    Text(it, color = headerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(
                    hw.text,
                    color = if (hw.done) Neon.Muted else Neon.Text,
                    fontSize = 15.sp,
                    textDecoration = if (hw.done) TextDecoration.LineThrough else null,
                )
                footer?.let {
                    Text(it, color = footerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Удалить", tint = Neon.Dim)
            }
        }
    }
}
