package com.alexshell.raspisanie.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.notify.Notifier
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.NeonTimePickerDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alexshell.raspisanie.ui.components.chipColors
import com.alexshell.raspisanie.ui.theme.Neon

@Composable
fun NotificationsCard(vm: AppViewModel) {
    val context = LocalContext.current
    val n = vm.notif
    var pickEvening by remember { mutableStateOf(false) }
    if (pickEvening) {
        NeonTimePickerDialog(
            title = "Вечерние напоминания",
            initial = n.eveningAt,
            onDismiss = { pickEvening = false },
            onConfirm = { vm.updateNotif(n.copy(eveningAt = it)); pickEvening = false },
        )
    }

    NeonCard(accent = Neon.Cyan) {
        // --- Разрешения ---
        PermissionRow(
            ok = vm.canNotify,
            okText = "Уведомления разрешены",
            badText = "Уведомления выключены",
            action = "Включить",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        PermissionRow(
            ok = vm.batteryUnrestricted,
            okText = "Работа в фоне разрешена",
            badText = "Система может «усыплять» напоминания",
            action = "Разрешить",
        ) {
            val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(direct) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            PermissionRow(
                ok = vm.canExact,
                okText = "Точные напоминания включены",
                badText = "Напоминания могут опаздывать",
                action = "Включить",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        }

        Divider()

        // --- Перед парой ---
        ToggleRow("Перед началом пары", n.beforeLesson) { vm.updateNotif(n.copy(beforeLesson = it)) }
        if (n.beforeLesson) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(5, 10, 15, 30, 60).forEach { m ->
                    FilterChip(
                        selected = n.leadMinutes == m,
                        onClick = { vm.updateNotif(n.copy(leadMinutes = m)) },
                        label = { Text(if (m == 60) "за 1 час" else "за $m мин") },
                        colors = chipColors(),
                    )
                }
            }
        }

        // --- Вечером ---
        ToggleRow("Пары на завтра (вечером)", n.evening) { vm.updateNotif(n.copy(evening = it)) }
        ToggleRow("Невыполненное ДЗ на завтра", n.homework) { vm.updateNotif(n.copy(homework = it)) }
        if (n.evening || n.homework) {
            OutlinedButton(
                onClick = { pickEvening = true },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.6f)),
            ) { Text("Вечерние напоминания в ${TimeSlot.fmt(n.eveningAt)}", color = Neon.Text) }
        }

        Divider()
        OutlinedButton(
            onClick = { Notifier.showTest(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Neon.Magenta),
        ) { Text("Проверить уведомление", color = Neon.Magenta, fontWeight = FontWeight.SemiBold) }
        Text(
            "На Xiaomi, Huawei, Honor и Samsung дополнительно включите «Автозапуск» и уберите " +
                "ограничения батареи для приложения в настройках телефона — иначе система может блокировать напоминания.",
            color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PermissionRow(ok: Boolean, okText: String, badText: String, action: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "!", color = if (ok) Neon.Lime else Neon.Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (ok) okText else badText,
            color = if (ok) Neon.Text else Neon.Amber,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            OutlinedButton(
                onClick = onFix,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Neon.Amber),
            ) { Text(action, color = Neon.Amber, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Neon.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Neon.Cyan,
                uncheckedThumbColor = Neon.Muted,
                uncheckedTrackColor = Neon.SurfaceHi,
                uncheckedBorderColor = Neon.Stroke,
            ),
        )
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Stroke))
    Spacer(Modifier.height(6.dp))
}
